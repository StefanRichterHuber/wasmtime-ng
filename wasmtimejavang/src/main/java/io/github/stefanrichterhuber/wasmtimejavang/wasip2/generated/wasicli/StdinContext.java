package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:cli/stdin" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface StdinContext extends WasmComponentContext {
    String INTERFACE = "wasi:cli/stdin";

    @Override
    default String name() {
        return "stdin";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "get-stdin", this::stdinGetStdinImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "input-stream", this::dropInputStream)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    WitResource stdinGetStdin(WasmtimeComponentInstance instance);

    void dropInputStream(int rep);

    private Object[] stdinGetStdinImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { stdinGetStdin(instance) };
    }

}
