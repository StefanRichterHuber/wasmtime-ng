package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;
import java.util.Optional;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:cli/terminal-stdout" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface TerminalStdoutContext extends WasmComponentContext {
    String INTERFACE = "wasi:cli/terminal-stdout";

    @Override
    default String name() {
        return "terminal-stdout";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "get-terminal-stdout", this::terminalStdoutGetTerminalStdoutImpl)
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

    Optional<Object> terminalStdoutGetTerminalStdout(WasmtimeComponentInstance instance);

    void dropTerminalOutput(int rep);

    private Object[] terminalStdoutGetTerminalStdoutImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { terminalStdoutGetTerminalStdout(instance) };
    }

}
