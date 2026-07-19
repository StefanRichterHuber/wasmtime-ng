package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:cli/exit" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface ExitContext extends WasmComponentContext {
    String INTERFACE = "wasi:cli/exit";

    @Override
    default String name() {
        return "exit";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "exit", this::exitExitImpl)
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

    void exitExit(WasmtimeComponentInstance instance, WitResult status);

    private Object[] exitExitImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResult status = (WitResult) args[0];
        exitExit(instance, status);
        return new Object[0];
    }

}
