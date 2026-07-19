package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:io/error" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface ErrorContext extends WasmComponentContext {
    String INTERFACE = "wasi:io/error";

    @Override
    default String name() {
        return "error";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "[method]error.to-debug-string", this::errorToDebugStringImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "error", this::dropError)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    String errorToDebugString(WasmtimeComponentInstance instance, WitResource self);

    void dropError(int rep);

    private Object[] errorToDebugStringImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { errorToDebugString(instance, self) };
    }

}
