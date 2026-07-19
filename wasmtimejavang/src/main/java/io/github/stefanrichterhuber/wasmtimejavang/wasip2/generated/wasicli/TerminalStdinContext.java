package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;
import java.util.Optional;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:cli/terminal-stdin" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface TerminalStdinContext extends WasmComponentContext {
    String INTERFACE = "wasi:cli/terminal-stdin";

    @Override
    default String name() {
        return "terminal-stdin";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "get-terminal-stdin", this::terminalStdinGetTerminalStdinImpl)
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

    Optional<Object> terminalStdinGetTerminalStdin(WasmtimeComponentInstance instance);

    void dropTerminalInput(int rep);

    private Object[] terminalStdinGetTerminalStdinImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { terminalStdinGetTerminalStdin(instance) };
    }

}
