package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitEnum;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:sockets/tcp-create-socket" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface TcpCreateSocketContext extends WasmComponentContext {
    String INTERFACE = "wasi:sockets/tcp-create-socket";

    @Override
    default String name() {
        return "tcp-create-socket";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "create-tcp-socket", this::tcpCreateSocketCreateTcpSocketImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "tcp-socket", this::dropTcpSocket)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    WitResult tcpCreateSocketCreateTcpSocket(WasmtimeComponentInstance instance, WitEnum addressFamily);

    void dropTcpSocket(int rep);

    private Object[] tcpCreateSocketCreateTcpSocketImpl(WasmtimeComponentInstance instance, Object... args) {
        WitEnum addressFamily = (WitEnum) args[0];
        return new Object[] { tcpCreateSocketCreateTcpSocket(instance, addressFamily) };
    }

}
