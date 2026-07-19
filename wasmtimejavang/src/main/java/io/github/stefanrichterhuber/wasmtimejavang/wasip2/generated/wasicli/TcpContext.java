package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitEnum;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:sockets/tcp" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface TcpContext extends WasmComponentContext {
    String INTERFACE = "wasi:sockets/tcp";

    @Override
    default String name() {
        return "tcp";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "[method]tcp-socket.start-bind", this::tcpSocketStartBindImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.finish-bind", this::tcpSocketFinishBindImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.start-connect", this::tcpSocketStartConnectImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.finish-connect", this::tcpSocketFinishConnectImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.start-listen", this::tcpSocketStartListenImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.finish-listen", this::tcpSocketFinishListenImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.accept", this::tcpSocketAcceptImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.local-address", this::tcpSocketLocalAddressImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.remote-address", this::tcpSocketRemoteAddressImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.is-listening", this::tcpSocketIsListeningImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.address-family", this::tcpSocketAddressFamilyImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.set-listen-backlog-size", this::tcpSocketSetListenBacklogSizeImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.keep-alive-enabled", this::tcpSocketKeepAliveEnabledImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.set-keep-alive-enabled", this::tcpSocketSetKeepAliveEnabledImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.keep-alive-idle-time", this::tcpSocketKeepAliveIdleTimeImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.set-keep-alive-idle-time", this::tcpSocketSetKeepAliveIdleTimeImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.keep-alive-interval", this::tcpSocketKeepAliveIntervalImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.set-keep-alive-interval", this::tcpSocketSetKeepAliveIntervalImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.keep-alive-count", this::tcpSocketKeepAliveCountImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.set-keep-alive-count", this::tcpSocketSetKeepAliveCountImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.hop-limit", this::tcpSocketHopLimitImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.set-hop-limit", this::tcpSocketSetHopLimitImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.receive-buffer-size", this::tcpSocketReceiveBufferSizeImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.set-receive-buffer-size", this::tcpSocketSetReceiveBufferSizeImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.send-buffer-size", this::tcpSocketSendBufferSizeImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.set-send-buffer-size", this::tcpSocketSetSendBufferSizeImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.subscribe", this::tcpSocketSubscribeImpl),
                new ComponentImportFunction(versioned(), "[method]tcp-socket.shutdown", this::tcpSocketShutdownImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "tcp-socket", this::dropTcpSocket),
                new ComponentImportResource(versioned(), "network", this::dropNetwork),
                new ComponentImportResource(versioned(), "input-stream", this::dropInputStream),
                new ComponentImportResource(versioned(), "output-stream", this::dropOutputStream),
                new ComponentImportResource(versioned(), "pollable", this::dropPollable)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    WitResult tcpSocketStartBind(WasmtimeComponentInstance instance, WitResource self, WitResource network, WitVariant localAddress);

    WitResult tcpSocketFinishBind(WasmtimeComponentInstance instance, WitResource self);

    WitResult tcpSocketStartConnect(WasmtimeComponentInstance instance, WitResource self, WitResource network, WitVariant remoteAddress);

    WitResult tcpSocketFinishConnect(WasmtimeComponentInstance instance, WitResource self);

    WitResult tcpSocketStartListen(WasmtimeComponentInstance instance, WitResource self);

    WitResult tcpSocketFinishListen(WasmtimeComponentInstance instance, WitResource self);

    WitResult tcpSocketAccept(WasmtimeComponentInstance instance, WitResource self);

    WitResult tcpSocketLocalAddress(WasmtimeComponentInstance instance, WitResource self);

    WitResult tcpSocketRemoteAddress(WasmtimeComponentInstance instance, WitResource self);

    boolean tcpSocketIsListening(WasmtimeComponentInstance instance, WitResource self);

    WitEnum tcpSocketAddressFamily(WasmtimeComponentInstance instance, WitResource self);

    WitResult tcpSocketSetListenBacklogSize(WasmtimeComponentInstance instance, WitResource self, long value);

    WitResult tcpSocketKeepAliveEnabled(WasmtimeComponentInstance instance, WitResource self);

    WitResult tcpSocketSetKeepAliveEnabled(WasmtimeComponentInstance instance, WitResource self, boolean value);

    WitResult tcpSocketKeepAliveIdleTime(WasmtimeComponentInstance instance, WitResource self);

    WitResult tcpSocketSetKeepAliveIdleTime(WasmtimeComponentInstance instance, WitResource self, long value);

    WitResult tcpSocketKeepAliveInterval(WasmtimeComponentInstance instance, WitResource self);

    WitResult tcpSocketSetKeepAliveInterval(WasmtimeComponentInstance instance, WitResource self, long value);

    WitResult tcpSocketKeepAliveCount(WasmtimeComponentInstance instance, WitResource self);

    WitResult tcpSocketSetKeepAliveCount(WasmtimeComponentInstance instance, WitResource self, int value);

    WitResult tcpSocketHopLimit(WasmtimeComponentInstance instance, WitResource self);

    WitResult tcpSocketSetHopLimit(WasmtimeComponentInstance instance, WitResource self, int value);

    WitResult tcpSocketReceiveBufferSize(WasmtimeComponentInstance instance, WitResource self);

    WitResult tcpSocketSetReceiveBufferSize(WasmtimeComponentInstance instance, WitResource self, long value);

    WitResult tcpSocketSendBufferSize(WasmtimeComponentInstance instance, WitResource self);

    WitResult tcpSocketSetSendBufferSize(WasmtimeComponentInstance instance, WitResource self, long value);

    WitResource tcpSocketSubscribe(WasmtimeComponentInstance instance, WitResource self);

    WitResult tcpSocketShutdown(WasmtimeComponentInstance instance, WitResource self, WitEnum shutdownType);

    void dropTcpSocket(int rep);

    void dropNetwork(int rep);

    void dropInputStream(int rep);

    void dropOutputStream(int rep);

    void dropPollable(int rep);

    private Object[] tcpSocketStartBindImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        WitResource network = (WitResource) args[1];
        WitVariant localAddress = (WitVariant) args[2];
        return new Object[] { tcpSocketStartBind(instance, self, network, localAddress) };
    }

    private Object[] tcpSocketFinishBindImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketFinishBind(instance, self) };
    }

    private Object[] tcpSocketStartConnectImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        WitResource network = (WitResource) args[1];
        WitVariant remoteAddress = (WitVariant) args[2];
        return new Object[] { tcpSocketStartConnect(instance, self, network, remoteAddress) };
    }

    private Object[] tcpSocketFinishConnectImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketFinishConnect(instance, self) };
    }

    private Object[] tcpSocketStartListenImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketStartListen(instance, self) };
    }

    private Object[] tcpSocketFinishListenImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketFinishListen(instance, self) };
    }

    private Object[] tcpSocketAcceptImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketAccept(instance, self) };
    }

    private Object[] tcpSocketLocalAddressImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketLocalAddress(instance, self) };
    }

    private Object[] tcpSocketRemoteAddressImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketRemoteAddress(instance, self) };
    }

    private Object[] tcpSocketIsListeningImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketIsListening(instance, self) };
    }

    private Object[] tcpSocketAddressFamilyImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketAddressFamily(instance, self) };
    }

    private Object[] tcpSocketSetListenBacklogSizeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long value = (Long) args[1];
        return new Object[] { tcpSocketSetListenBacklogSize(instance, self, value) };
    }

    private Object[] tcpSocketKeepAliveEnabledImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketKeepAliveEnabled(instance, self) };
    }

    private Object[] tcpSocketSetKeepAliveEnabledImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        boolean value = (Boolean) args[1];
        return new Object[] { tcpSocketSetKeepAliveEnabled(instance, self, value) };
    }

    private Object[] tcpSocketKeepAliveIdleTimeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketKeepAliveIdleTime(instance, self) };
    }

    private Object[] tcpSocketSetKeepAliveIdleTimeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long value = (Long) args[1];
        return new Object[] { tcpSocketSetKeepAliveIdleTime(instance, self, value) };
    }

    private Object[] tcpSocketKeepAliveIntervalImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketKeepAliveInterval(instance, self) };
    }

    private Object[] tcpSocketSetKeepAliveIntervalImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long value = (Long) args[1];
        return new Object[] { tcpSocketSetKeepAliveInterval(instance, self, value) };
    }

    private Object[] tcpSocketKeepAliveCountImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketKeepAliveCount(instance, self) };
    }

    private Object[] tcpSocketSetKeepAliveCountImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        int value = (Integer) args[1];
        return new Object[] { tcpSocketSetKeepAliveCount(instance, self, value) };
    }

    private Object[] tcpSocketHopLimitImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketHopLimit(instance, self) };
    }

    private Object[] tcpSocketSetHopLimitImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        int value = (Integer) args[1];
        return new Object[] { tcpSocketSetHopLimit(instance, self, value) };
    }

    private Object[] tcpSocketReceiveBufferSizeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketReceiveBufferSize(instance, self) };
    }

    private Object[] tcpSocketSetReceiveBufferSizeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long value = (Long) args[1];
        return new Object[] { tcpSocketSetReceiveBufferSize(instance, self, value) };
    }

    private Object[] tcpSocketSendBufferSizeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketSendBufferSize(instance, self) };
    }

    private Object[] tcpSocketSetSendBufferSizeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long value = (Long) args[1];
        return new Object[] { tcpSocketSetSendBufferSize(instance, self, value) };
    }

    private Object[] tcpSocketSubscribeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { tcpSocketSubscribe(instance, self) };
    }

    private Object[] tcpSocketShutdownImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        WitEnum shutdownType = (WitEnum) args[1];
        return new Object[] { tcpSocketShutdown(instance, self, shutdownType) };
    }

}
