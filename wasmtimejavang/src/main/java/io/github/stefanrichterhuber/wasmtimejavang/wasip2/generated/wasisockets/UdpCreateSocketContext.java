package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasisockets;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitEnum;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:sockets/udp-create-socket" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface UdpCreateSocketContext extends WasmComponentContext {
    String INTERFACE = "wasi:sockets/udp-create-socket";

    @Override
    default String name() {
        return "udp-create-socket";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "create-udp-socket", this::udpCreateSocketCreateUdpSocketImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "udp-socket", this::dropUdpSocket)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    WitResult udpCreateSocketCreateUdpSocket(WasmtimeComponentInstance instance, WitEnum addressFamily);

    void dropUdpSocket(int rep);

    private Object[] udpCreateSocketCreateUdpSocketImpl(WasmtimeComponentInstance instance, Object... args) {
        WitEnum addressFamily = (WitEnum) args[0];
        return new Object[] { udpCreateSocketCreateUdpSocket(instance, addressFamily) };
    }

}
