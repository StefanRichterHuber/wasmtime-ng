package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:cli/stdout" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface StdoutContext extends WasmComponentContext {
    String INTERFACE = "wasi:cli/stdout";

    @Override
    default String name() {
        return "stdout";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "get-stdout", this::stdoutGetStdoutImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "output-stream", this::dropOutputStream)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    WitResource stdoutGetStdout(WasmtimeComponentInstance instance);

    void dropOutputStream(int rep);

    private Object[] stdoutGetStdoutImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { stdoutGetStdout(instance) };
    }

}
