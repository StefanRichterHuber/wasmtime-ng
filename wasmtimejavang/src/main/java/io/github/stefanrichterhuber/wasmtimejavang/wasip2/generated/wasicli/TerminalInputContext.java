package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;


import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:cli/terminal-input" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface TerminalInputContext extends WasmComponentContext {
    String INTERFACE = "wasi:cli/terminal-input";

    @Override
    default String name() {
        return "terminal-input";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "terminal-input", this::dropTerminalInput)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    void dropTerminalInput(int rep);

}
