package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link ComponentContextLookup} backed by an explicit, manually populated
 * registry -- useful when auto-discovery via
 * {@link ServiceLoaderComponentContextLookup} is undesirable: for example in
 * tests, or when several differently-configured instances of the same
 * dependency name could exist and the caller wants full control over which
 * one dependents are wired up to.
 */
public class RegistryComponentContextLookup implements ComponentContextLookup {
    private final Map<String, WasmComponentContext> registry = new LinkedHashMap<>();

    /**
     * Registers the instance to return for the given dependency name.
     *
     * @param name    The dependency name (e.g. {@code "wasi-io"}).
     * @param context The instance to return when {@code name} is resolved.
     * @return This lookup, for chaining.
     */
    public RegistryComponentContextLookup register(String name, WasmComponentContext context) {
        registry.put(Objects.requireNonNull(name), Objects.requireNonNull(context));
        return this;
    }

    @Override
    public Optional<WasmComponentContext> resolve(String name) {
        return Optional.ofNullable(registry.get(name));
    }

    @Override
    public Optional<WasmComponentContext> resolveProviding(String interfaceName) {
        return registry.values().stream()
                .filter(context -> context.getProvidedInterfaces().contains(interfaceName))
                .findFirst();
    }
}
