package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:sockets/instance-network" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface InstanceNetworkContext extends WasmComponentContext {
    String INTERFACE = "wasi:sockets/instance-network";

    @Override
    default String name() {
        return "instance-network";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "instance-network", this::instanceNetworkInstanceNetworkImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "network", this::dropNetwork)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    WitResource instanceNetworkInstanceNetwork(WasmtimeComponentInstance instance);

    void dropNetwork(int rep);

    private Object[] instanceNetworkInstanceNetworkImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { instanceNetworkInstanceNetwork(instance) };
    }

}
