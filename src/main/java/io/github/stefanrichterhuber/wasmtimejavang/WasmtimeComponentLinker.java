package io.github.stefanrichterhuber.wasmtimejavang;

import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext.ComponentImportFunction;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext.ComponentImportResource;

/**
 * Used to resolve imports for a WebAssembly component.
 * Mirrors {@link WasmtimeLinker}, adapted to the Component Model: instead of
 * primitive-typed core wasm functions, imports are registered dynamically
 * using {@link ComponentFunction}, whose arguments/return values are plain
 * Java representations of WIT values (see {@link ComponentFunction}).
 */
public final class WasmtimeComponentLinker implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger();

    private long linkerPtr;

    private final WasmtimeEngine engine;

    private final WasmtimeStore store;

    private final List<WasmComponentContext> contexts = new ArrayList<>();

    /**
     * Contexts already linked on this linker, keyed by
     * {@link WasmComponentContext#name()}
     * -- populated both by explicit {@link #linkContext} calls and by
     * dependency resolution, so a dependency shared by several contexts
     * (e.g. several contexts all depending on {@code "wasi-io"}) is only
     * ever linked -- and thus only ever instantiated as a shared
     * table-owner -- once, and every dependent shares that same instance.
     */
    private final Map<String, WasmComponentContext> linkedByName = new LinkedHashMap<>();

    /**
     * The strategy used to resolve a context's declared dependencies (see
     * {@link WasmComponentContext#getDependencies()}) and, if used, the
     * providers looked up by {@link #linkRequired(WasmtimeComponent)}.
     * <br>
     * Itself resolved via {@link ServiceLoader}: registering a
     * {@code META-INF/services/io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup}
     * provider overrides the strategy for every {@code WasmtimeComponentLinker}
     * without any call site needing to know about it, the same "swap the
     * default via SPI, not a setter" approach used for
     * {@link WasmComponentContext} implementations themselves. Falls back to
     * a {@link ServiceLoaderComponentContextLookup} if nothing is registered.
     */
    private final ComponentContextLookup dependencyLookup = ServiceLoader.load(ComponentContextLookup.class)
            .findFirst()
            .orElseGet(ServiceLoaderComponentContextLookup::new);

    private native long createLinker(long enginePtr);

    private native static void closeLinker(long linkerPtr);

    /**
     * Registers every resource and function belonging to a single component
     * interface in one native call.
     * <br>
     * This is intentionally batched per-interface rather than one call per
     * function/resource (like the core-wasm {@link WasmtimeLinker} does):
     * wasmtime's {@code component::Linker::instance(name)} errors if called
     * more than once for the same interface name, so every import belonging
     * to one interface must be registered while a single
     * {@code LinkerInstance} borrow is alive on the Rust side.
     */
    private native void defineComponentInterface(
            long storePtr, long linkerPtr,
            String interfaceName,
            List<String> resourceNames, List<ResourceDestructor> destructors,
            List<String> funcNames, List<ComponentFunction> functions);

    private final Cleaner.Cleanable cleanable;

    private static class CleanState implements Runnable {
        private final long linkerPtr;

        CleanState(long linkerPtr) {
            this.linkerPtr = linkerPtr;
        }

        @Override
        public void run() {
            WasmtimeComponentLinker.closeLinker(linkerPtr);
        }
    }

    /**
     * Creates a new WasmtimeComponentLinker.
     *
     * @param engine The engine associated with this linker.
     * @param store  The store associated with this linker.
     */
    public WasmtimeComponentLinker(WasmtimeEngine engine, WasmtimeStore store) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.linkerPtr = createLinker(engine.getEnginePtr());
        this.cleanable = WasmtimeEngine.CLEANER.register(this, new CleanState(this.linkerPtr));
    }

    /**
     * Closes the linker and releases native resources.
     */
    @Override
    public void close() throws Exception {
        if (linkerPtr != 0) {
            this.cleanable.clean();
        }
        linkerPtr = 0;
    }

    /**
     * Returns the native pointer to the linker.
     *
     * @return The native linker pointer.
     */
    long getLinkerPtr() {
        if (linkerPtr == 0) {
            throw new IllegalStateException("Linker no longer active");
        }
        return this.linkerPtr;
    }

    /**
     * Links all import functions and resources from a WasmComponentContext,
     * first resolving and linking any dependencies it declares via
     * {@link WasmComponentContext#getDependencies()} (using the configured
     * {@link ComponentContextLookup}) that aren't already linked.
     *
     * @param context The context providing imports (e.g. a WASI Preview 2
     *                context).
     * @return This WasmtimeComponentLinker for method chaining
     */
    public WasmtimeComponentLinker linkContext(WasmComponentContext context) {
        linkContext(context, new HashSet<>());
        return this;
    }

    private void linkContext(WasmComponentContext context, Set<String> inProgress) {
        WasmComponentContext existing = linkedByName.get(context.name());
        if (existing != null) {
            if (existing != context) {
                LOGGER.debug("Context name {} already linked with a different instance; keeping the existing one",
                        context.name());
            }
            return;
        }
        if (!inProgress.add(context.name())) {
            throw new IllegalStateException(
                    "Circular dependency detected involving component context \"" + context.name() + "\"");
        }

        for (String dependencyName : context.getDependencies()) {
            linkContext(resolveDependency(context, dependencyName), inProgress);
        }

        context.onDependenciesResolved((name, version) -> Optional.ofNullable(linkedByName.get(name)));

        this.contexts.add(context);
        linkedByName.put(context.name(), context);
        inProgress.remove(context.name());
        LOGGER.debug("Adding component context {} to store", context.name());

        registerImports(context);
    }

    /**
     * Links whatever this linker doesn't already have linked to satisfy
     * {@code component}'s actual interface imports (read directly off the
     * compiled component via {@link WasmtimeComponent#getImportInterfaces()}
     * -- no instantiation needed), resolving a provider for each one via the
     * configured {@link ComponentContextLookup} (see
     * {@link ComponentContextLookup#resolveProviding(String)}).
     * <br>
     * This is what makes linking adapt to whatever a given component
     * actually needs instead of the caller having to know and explicitly
     * link every interface group up front: a component that doesn't use
     * {@code wasi:clocks}, for example, simply won't cause anything
     * implementing it to be linked. Contexts that need specific
     * configuration (e.g. capturing stdout) should still be linked
     * explicitly via {@link #linkContext(WasmComponentContext)} beforehand --
     * this method only fills in whatever remains unresolved.
     *
     * @param component The component to read required interfaces from.
     * @return This WasmtimeComponentLinker for method chaining
     * @throws IllegalStateException if any required interface has no linked
     *                               context and none could be resolved via
     *                               the configured lookup.
     */
    public WasmtimeComponentLinker linkRequired(WasmtimeComponent component) {
        List<String> unresolved = new ArrayList<>();
        for (String interfaceName : component.getImportInterfaces()) {
            if (isInterfaceLinked(interfaceName)) {
                continue;
            }
            Optional<WasmComponentContext> provider = dependencyLookup.resolveProviding(interfaceName);
            if (provider.isEmpty()) {
                unresolved.add(interfaceName);
                continue;
            }
            linkContext(provider.get());
        }
        if (!unresolved.isEmpty()) {
            throw new IllegalStateException(
                    "No linked WasmComponentContext provides required interface(s) " + unresolved + " -- link one "
                            + "explicitly first, register a ServiceLoader provider for it, or override the "
                            + "dependency lookup strategy via a ComponentContextLookup ServiceLoader provider");
        }
        return this;
    }

    private boolean isInterfaceLinked(String interfaceName) {
        String bareName = ComponentContextLookup.bareInterfaceName(interfaceName);
        for (WasmComponentContext context : linkedByName.values()) {
            if (context.getProvidedInterfaces().contains(bareName)) {
                return true;
            }
        }
        return false;
    }

    private WasmComponentContext resolveDependency(WasmComponentContext dependent, String dependencyName) {
        WasmComponentContext existing = linkedByName.get(dependencyName);
        if (existing != null) {
            return existing;
        }

        return dependencyLookup.resolve(dependencyName, dependent.getVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "Component context \"" + dependent.name() + "\" depends on \"" + dependencyName
                                + "\", but no such context is linked and none could be resolved via "
                                + dependencyLookup.getClass().getSimpleName()
                                + " -- link one explicitly first, register a ServiceLoader provider for it, "
                                + "or override the dependency lookup strategy via a ComponentContextLookup "
                                + "ServiceLoader provider"));
    }

    /**
     * Registers a single context's own import functions/resources (not its
     * dependencies, which {@link #linkContext(WasmComponentContext, Set)}
     * already linked beforehand).
     */
    private void registerImports(WasmComponentContext context) {
        Map<String, List<ComponentImportResource>> resourcesByInterface = new LinkedHashMap<>();
        for (ComponentImportResource resource : context.getImportResources()) {
            resourcesByInterface.computeIfAbsent(resource.interfaceName(), k -> new ArrayList<>()).add(resource);
        }

        Map<String, List<ComponentImportFunction>> functionsByInterface = new LinkedHashMap<>();
        for (ComponentImportFunction function : context.getImportFunctions()) {
            functionsByInterface.computeIfAbsent(function.interfaceName(), k -> new ArrayList<>()).add(function);
        }

        Set<String> interfaceNames = new LinkedHashSet<>();
        interfaceNames.addAll(resourcesByInterface.keySet());
        interfaceNames.addAll(functionsByInterface.keySet());

        for (String interfaceName : interfaceNames) {
            List<ComponentImportResource> resources = resourcesByInterface.getOrDefault(interfaceName, List.of());
            List<ComponentImportFunction> functions = functionsByInterface.getOrDefault(interfaceName, List.of());

            List<String> resourceNames = new ArrayList<>(resources.size());
            List<ResourceDestructor> destructors = new ArrayList<>(resources.size());
            for (ComponentImportResource resource : resources) {
                resourceNames.add(resource.resourceName());
                destructors.add(resource.destructor());
            }

            List<String> funcNames = new ArrayList<>(functions.size());
            List<ComponentFunction> funcs = new ArrayList<>(functions.size());
            for (ComponentImportFunction function : functions) {
                funcNames.add(function.funcName());
                funcs.add(function.function());
            }

            this.defineComponentInterface(getStore().getStorePtr(), getLinkerPtr(), interfaceName,
                    resourceNames, destructors, funcNames, funcs);
        }
    }

    /**
     * Returns the WasmtimeStore of this linker.
     *
     * @return The WasmtimeStore object
     */
    public WasmtimeStore getStore() {
        return this.store;
    }

    /**
     * Returns the WasmtimeEngine of this linker.
     *
     * @return The WasmtimeEngine object
     */
    public WasmtimeEngine getEngine() {
        return this.engine;
    }

    /**
     * Returns a copy of the list of contexts that have been linked.
     *
     * @return A copy of the list of contexts.
     */
    public List<WasmComponentContext> getContexts() {
        return new ArrayList<>(this.contexts);
    }
}
