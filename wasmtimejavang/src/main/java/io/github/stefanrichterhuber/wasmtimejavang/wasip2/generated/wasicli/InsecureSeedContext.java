package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;


import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:random/insecure-seed" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface InsecureSeedContext extends WasmComponentContext {
    String INTERFACE = "wasi:random/insecure-seed";

    @Override
    default String name() {
        return "insecure-seed";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "insecure-seed", this::insecureSeedInsecureSeedImpl)
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

    Object[] insecureSeedInsecureSeed(WasmtimeComponentInstance instance);

    private Object[] insecureSeedInsecureSeedImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { insecureSeedInsecureSeed(instance) };
    }

}
