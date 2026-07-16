package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.List;
import java.util.Set;

/**
 * Interface for providing a context of imported functions and resources to a
 * WASM component. Mirrors {@link WasmContext}, adapted to the Component
 * Model's richer value/resource model.
 */
public interface WasmComponentContext {

    /**
     * Represents an imported component function definition.
     *
     * @param interfaceName The name of the component interface providing the
     *                       function (e.g. {@code "wasi:cli/environment@0.2.3"},
     *                       or the empty string for the root instance).
     * @param funcName       The name of the function within the interface.
     * @param function       The Java implementation of the function.
     */
    public record ComponentImportFunction(String interfaceName, String funcName, ComponentFunction function) {
    }

    /**
     * Represents an imported resource type definition. The destructor is
     * invoked by the runtime when the guest drops an owned instance of this
     * resource.
     *
     * @param interfaceName The name of the component interface providing the
     *                       resource.
     * @param resourceName   The name of the resource within the interface.
     * @param destructor     Invoked with the resource's opaque {@code rep} when
     *                       an owned instance is dropped by the guest.
     */
    public record ComponentImportResource(String interfaceName, String resourceName, ResourceDestructor destructor) {
    }

    /**
     * Returns the list of functions provided by this context.
     *
     * @return A list of ComponentImportFunction objects.
     */
    List<ComponentImportFunction> getImportFunctions();

    /**
     * Returns the list of resource types provided by this context.
     *
     * @return A list of ComponentImportResource objects.
     */
    List<ComponentImportResource> getImportResources();

    /**
     * Returns the name of this context.
     * <br>
     * Besides identifying the context in logs, this name is also the stable
     * identifier other contexts reference when declaring it as a dependency
     * via {@link #getDependencies()} (e.g. {@code "wasi-io"}), and the key
     * {@link WasmtimeComponentLinker} caches linked contexts under -- so
     * implementations should keep it fixed rather than deriving it from
     * mutable configuration.
     *
     * @return The name of this context.
     */
    String name();

    /**
     * Declares the names of other component contexts this context needs in
     * order to function (e.g. a context implementing {@code wasi:cli/stdout}
     * depending on {@code "wasi-io"} to hand off the actual stream table).
     * <br>
     * {@link WasmtimeComponentLinker} resolves each declared name -- via its
     * configured {@link ComponentContextLookup} -- and links the resolved
     * context first, before this context's own imports are registered and
     * before {@link #onDependenciesResolved(ComponentContextLookup)} is
     * called. Referencing by name rather than by concrete type is what makes
     * a dependency swappable: any context registered/resolvable under that
     * name satisfies it, regardless of implementation.
     *
     * @return The names of contexts this context depends on. Empty by
     *         default.
     */
    default List<String> getDependencies() {
        return List.of();
    }

    /**
     * Called by {@link WasmtimeComponentLinker} once every name declared in
     * {@link #getDependencies()} has been resolved and linked, before
     * {@link #getImportFunctions()} / {@link #getImportResources()} are
     * queried. Implementations that declared dependencies should use this to
     * look up and cast them to whatever contract they actually need (e.g. a
     * shared resource-table interface), which is guaranteed present for
     * anything declared as a dependency.
     * <br>
     * Default no-op, for contexts with no dependencies.
     *
     * @param lookup Resolves any context also linked so far on this linker
     *               (by name), including but not limited to this context's
     *               own declared dependencies.
     */
    default void onDependenciesResolved(ComponentContextLookup lookup) {
        // No-op by default.
    }

    /**
     * Declares the names of the component interfaces this context actually
     * implements (e.g. {@code "wasi:io/poll@0.2.6"}, {@code "wasi:io/streams@0.2.6"}).
     * <br>
     * Used by {@link WasmtimeComponentLinker#linkRequired(WasmtimeComponent)}
     * to automatically find and link a context providing a specific
     * interface a component actually needs (read straight off the compiled
     * component via {@link WasmtimeComponent#getImportInterfaces()}), instead
     * of the caller having to know and explicitly link every interface group
     * up front.
     * <br>
     * Not derived automatically from {@link #getImportFunctions()} /
     * {@link #getImportResources()} because building those lists can require
     * a resolved dependency (see {@link #onDependenciesResolved}) that isn't
     * available yet when interfaces are being matched against a not-yet-linked
     * component -- so implementations declare this set directly (it should
     * list the same interface names that appear in
     * {@code getImportFunctions()}/{@code getImportResources()}).
     *
     * @return The names of the interfaces this context implements. Empty by
     *         default.
     */
    default Set<String> getProvidedInterfaces() {
        return Set.of();
    }
}
