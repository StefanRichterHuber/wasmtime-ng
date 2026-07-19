package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;


import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:filesystem/preopens" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface PreopensContext extends WasmComponentContext {
    String INTERFACE = "wasi:filesystem/preopens";

    @Override
    default String name() {
        return "preopens";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "get-directories", this::preopensGetDirectoriesImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "descriptor", this::dropDescriptor)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    List<Object> preopensGetDirectories(WasmtimeComponentInstance instance);

    void dropDescriptor(int rep);

    private Object[] preopensGetDirectoriesImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { preopensGetDirectories(instance) };
    }

}
