package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;


import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:random/insecure" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface InsecureContext extends WasmComponentContext {
    String INTERFACE = "wasi:random/insecure";

    @Override
    default String name() {
        return "insecure";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "get-insecure-random-bytes", this::insecureGetInsecureRandomBytesImpl),
                new ComponentImportFunction(versioned(), "get-insecure-random-u64", this::insecureGetInsecureRandomU64Impl)
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

    byte[] insecureGetInsecureRandomBytes(WasmtimeComponentInstance instance, long len);

    long insecureGetInsecureRandomU64(WasmtimeComponentInstance instance);

    private Object[] insecureGetInsecureRandomBytesImpl(WasmtimeComponentInstance instance, Object... args) {
        long len = (Long) args[0];
        return new Object[] { insecureGetInsecureRandomBytes(instance, len) };
    }

    private Object[] insecureGetInsecureRandomU64Impl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { insecureGetInsecureRandomU64(instance) };
    }

}
