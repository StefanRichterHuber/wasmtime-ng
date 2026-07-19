package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;
import java.util.Optional;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:cli/terminal-stderr" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface TerminalStderrContext extends WasmComponentContext {
    String INTERFACE = "wasi:cli/terminal-stderr";

    @Override
    default String name() {
        return "terminal-stderr";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "get-terminal-stderr", this::terminalStderrGetTerminalStderrImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "terminal-output", this::dropTerminalOutput)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    Optional<Object> terminalStderrGetTerminalStderr(WasmtimeComponentInstance instance);

    void dropTerminalOutput(int rep);

    private Object[] terminalStderrGetTerminalStderrImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { terminalStderrGetTerminalStderr(instance) };
    }

}
