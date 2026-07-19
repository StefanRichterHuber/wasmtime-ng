package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasisockets;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitEnum;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;
import java.util.Optional;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:sockets/udp" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface UdpContext extends WasmComponentContext {
    String INTERFACE = "wasi:sockets/udp";

    @Override
    default String name() {
        return "udp";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "[method]udp-socket.start-bind", this::udpSocketStartBindImpl),
                new ComponentImportFunction(versioned(), "[method]udp-socket.finish-bind", this::udpSocketFinishBindImpl),
                new ComponentImportFunction(versioned(), "[method]udp-socket.stream", this::udpSocketStreamImpl),
                new ComponentImportFunction(versioned(), "[method]udp-socket.local-address", this::udpSocketLocalAddressImpl),
                new ComponentImportFunction(versioned(), "[method]udp-socket.remote-address", this::udpSocketRemoteAddressImpl),
                new ComponentImportFunction(versioned(), "[method]udp-socket.address-family", this::udpSocketAddressFamilyImpl),
                new ComponentImportFunction(versioned(), "[method]udp-socket.unicast-hop-limit", this::udpSocketUnicastHopLimitImpl),
                new ComponentImportFunction(versioned(), "[method]udp-socket.set-unicast-hop-limit", this::udpSocketSetUnicastHopLimitImpl),
                new ComponentImportFunction(versioned(), "[method]udp-socket.receive-buffer-size", this::udpSocketReceiveBufferSizeImpl),
                new ComponentImportFunction(versioned(), "[method]udp-socket.set-receive-buffer-size", this::udpSocketSetReceiveBufferSizeImpl),
                new ComponentImportFunction(versioned(), "[method]udp-socket.send-buffer-size", this::udpSocketSendBufferSizeImpl),
                new ComponentImportFunction(versioned(), "[method]udp-socket.set-send-buffer-size", this::udpSocketSetSendBufferSizeImpl),
                new ComponentImportFunction(versioned(), "[method]udp-socket.subscribe", this::udpSocketSubscribeImpl),
                new ComponentImportFunction(versioned(), "[method]incoming-datagram-stream.receive", this::incomingDatagramStreamReceiveImpl),
                new ComponentImportFunction(versioned(), "[method]incoming-datagram-stream.subscribe", this::incomingDatagramStreamSubscribeImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-datagram-stream.check-send", this::outgoingDatagramStreamCheckSendImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-datagram-stream.send", this::outgoingDatagramStreamSendImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-datagram-stream.subscribe", this::outgoingDatagramStreamSubscribeImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "udp-socket", this::dropUdpSocket),
                new ComponentImportResource(versioned(), "incoming-datagram-stream", this::dropIncomingDatagramStream),
                new ComponentImportResource(versioned(), "outgoing-datagram-stream", this::dropOutgoingDatagramStream),
                new ComponentImportResource(versioned(), "network", this::dropNetwork),
                new ComponentImportResource(versioned(), "pollable", this::dropPollable)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    WitResult udpSocketStartBind(WasmtimeComponentInstance instance, WitResource self, WitResource network, WitVariant localAddress);

    WitResult udpSocketFinishBind(WasmtimeComponentInstance instance, WitResource self);

    WitResult udpSocketStream(WasmtimeComponentInstance instance, WitResource self, Optional<Object> remoteAddress);

    WitResult udpSocketLocalAddress(WasmtimeComponentInstance instance, WitResource self);

    WitResult udpSocketRemoteAddress(WasmtimeComponentInstance instance, WitResource self);

    WitEnum udpSocketAddressFamily(WasmtimeComponentInstance instance, WitResource self);

    WitResult udpSocketUnicastHopLimit(WasmtimeComponentInstance instance, WitResource self);

    WitResult udpSocketSetUnicastHopLimit(WasmtimeComponentInstance instance, WitResource self, int value);

    WitResult udpSocketReceiveBufferSize(WasmtimeComponentInstance instance, WitResource self);

    WitResult udpSocketSetReceiveBufferSize(WasmtimeComponentInstance instance, WitResource self, long value);

    WitResult udpSocketSendBufferSize(WasmtimeComponentInstance instance, WitResource self);

    WitResult udpSocketSetSendBufferSize(WasmtimeComponentInstance instance, WitResource self, long value);

    WitResource udpSocketSubscribe(WasmtimeComponentInstance instance, WitResource self);

    WitResult incomingDatagramStreamReceive(WasmtimeComponentInstance instance, WitResource self, long maxResults);

    WitResource incomingDatagramStreamSubscribe(WasmtimeComponentInstance instance, WitResource self);

    WitResult outgoingDatagramStreamCheckSend(WasmtimeComponentInstance instance, WitResource self);

    WitResult outgoingDatagramStreamSend(WasmtimeComponentInstance instance, WitResource self, List<Object> datagrams);

    WitResource outgoingDatagramStreamSubscribe(WasmtimeComponentInstance instance, WitResource self);

    void dropUdpSocket(int rep);

    void dropIncomingDatagramStream(int rep);

    void dropOutgoingDatagramStream(int rep);

    void dropNetwork(int rep);

    void dropPollable(int rep);

    private Object[] udpSocketStartBindImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        WitResource network = (WitResource) args[1];
        WitVariant localAddress = (WitVariant) args[2];
        return new Object[] { udpSocketStartBind(instance, self, network, localAddress) };
    }

    private Object[] udpSocketFinishBindImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { udpSocketFinishBind(instance, self) };
    }

    private Object[] udpSocketStreamImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        Optional<Object> remoteAddress = (Optional<Object>) args[1];
        return new Object[] { udpSocketStream(instance, self, remoteAddress) };
    }

    private Object[] udpSocketLocalAddressImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { udpSocketLocalAddress(instance, self) };
    }

    private Object[] udpSocketRemoteAddressImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { udpSocketRemoteAddress(instance, self) };
    }

    private Object[] udpSocketAddressFamilyImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { udpSocketAddressFamily(instance, self) };
    }

    private Object[] udpSocketUnicastHopLimitImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { udpSocketUnicastHopLimit(instance, self) };
    }

    private Object[] udpSocketSetUnicastHopLimitImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        int value = (Integer) args[1];
        return new Object[] { udpSocketSetUnicastHopLimit(instance, self, value) };
    }

    private Object[] udpSocketReceiveBufferSizeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { udpSocketReceiveBufferSize(instance, self) };
    }

    private Object[] udpSocketSetReceiveBufferSizeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long value = (Long) args[1];
        return new Object[] { udpSocketSetReceiveBufferSize(instance, self, value) };
    }

    private Object[] udpSocketSendBufferSizeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { udpSocketSendBufferSize(instance, self) };
    }

    private Object[] udpSocketSetSendBufferSizeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long value = (Long) args[1];
        return new Object[] { udpSocketSetSendBufferSize(instance, self, value) };
    }

    private Object[] udpSocketSubscribeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { udpSocketSubscribe(instance, self) };
    }

    private Object[] incomingDatagramStreamReceiveImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long maxResults = (Long) args[1];
        return new Object[] { incomingDatagramStreamReceive(instance, self, maxResults) };
    }

    private Object[] incomingDatagramStreamSubscribeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { incomingDatagramStreamSubscribe(instance, self) };
    }

    private Object[] outgoingDatagramStreamCheckSendImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outgoingDatagramStreamCheckSend(instance, self) };
    }

    private Object[] outgoingDatagramStreamSendImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        List<Object> datagrams = (List<Object>) args[1];
        return new Object[] { outgoingDatagramStreamSend(instance, self, datagrams) };
    }

    private Object[] outgoingDatagramStreamSubscribeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outgoingDatagramStreamSubscribe(instance, self) };
    }

}
