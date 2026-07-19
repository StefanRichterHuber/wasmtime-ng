package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;


import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:random/random" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface RandomContext extends WasmComponentContext {
    String INTERFACE = "wasi:random/random";

    @Override
    default String name() {
        return "random";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "get-random-bytes", this::randomGetRandomBytesImpl),
                new ComponentImportFunction(versioned(), "get-random-u64", this::randomGetRandomU64Impl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of();
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    byte[] randomGetRandomBytes(WasmtimeComponentInstance instance, long len);

    long randomGetRandomU64(WasmtimeComponentInstance instance);

    private Object[] randomGetRandomBytesImpl(WasmtimeComponentInstance instance, Object... args) {
        long len = (Long) args[0];
        return new Object[] { randomGetRandomBytes(instance, len) };
    }

    private Object[] randomGetRandomU64Impl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { randomGetRandomU64(instance) };
    }

}
