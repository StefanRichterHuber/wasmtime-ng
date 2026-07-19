package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;
import java.util.Optional;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:cli/environment" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface EnvironmentContext extends WasmComponentContext {
    String INTERFACE = "wasi:cli/environment";

    @Override
    default String name() {
        return "environment";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "get-environment", this::environmentGetEnvironmentImpl),
                new ComponentImportFunction(versioned(), "get-arguments", this::environmentGetArgumentsImpl),
                new ComponentImportFunction(versioned(), "initial-cwd", this::environmentInitialCwdImpl)
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

    List<Object> environmentGetEnvironment(WasmtimeComponentInstance instance);

    List<Object> environmentGetArguments(WasmtimeComponentInstance instance);

    Optional<Object> environmentInitialCwd(WasmtimeComponentInstance instance);

    private Object[] environmentGetEnvironmentImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { environmentGetEnvironment(instance) };
    }

    private Object[] environmentGetArgumentsImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { environmentGetArguments(instance) };
    }

    private Object[] environmentInitialCwdImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { environmentInitialCwd(instance) };
    }

}
