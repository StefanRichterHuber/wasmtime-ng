package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.Optional;

/**
 * Pluggable strategy for resolving a {@link WasmComponentContext} dependency
 * -- declared by name via {@link WasmComponentContext#getDependencies()} -- to
 * an actual instance.
 * <br>
 * {@link WasmtimeComponentLinker} only consults its configured lookup when a
 * name hasn't already been linked (explicitly, or as someone else's
 * already-resolved dependency): the linker's own "already linked" cache
 * always wins first, so every dependent sharing the same dependency name gets
 * the same instance.
 * <br>
 * The linker resolves which strategy to use itself via {@link java.util.ServiceLoader}
 * (a {@code META-INF/services/io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup}
 * provider), falling back to {@link ServiceLoaderComponentContextLookup} if
 * none is registered -- so overriding it is a matter of registering a
 * provider, not calling a setter.
 *
 * @see ServiceLoaderComponentContextLookup the default strategy
 * @see RegistryComponentContextLookup an explicit, manually populated
 *      alternative
 */
@FunctionalInterface
public interface ComponentContextLookup {
    /**
     * Attempts to resolve an instance providing the named context.
     *
     * @param name The dependency name to resolve (e.g. {@code "wasi-io"}).
     * @return An instance whose {@link WasmComponentContext#name()} is
     *         {@code name}, or empty if this strategy has none.
     */
    Optional<WasmComponentContext> resolve(String name);

    /**
     * Attempts to resolve an instance implementing the given component
     * interface, used by {@link WasmtimeComponentLinker#linkRequired(WasmtimeComponent)}
     * to auto-link whatever a component actually needs. Unlike
     * {@link #resolve(String)} (a context's own stable name), this matches
     * against {@link WasmComponentContext#getProvidedInterfaces()}.
     * <br>
     * Not abstract: strategies that only support name-based resolution (e.g.
     * a minimal custom implementation) don't need to implement this too.
     *
     * @param interfaceName The component interface name to resolve a
     *                      provider for (e.g. {@code "wasi:io/poll@0.2.6"}).
     * @return An instance whose {@link WasmComponentContext#getProvidedInterfaces()}
     *         contains {@code interfaceName}, or empty if this strategy has
     *         none.
     */
    default Optional<WasmComponentContext> resolveProviding(String interfaceName) {
        return Optional.empty();
    }
}
