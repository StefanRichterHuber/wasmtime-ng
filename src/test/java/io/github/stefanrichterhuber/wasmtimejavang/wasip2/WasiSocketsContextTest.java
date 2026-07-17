package io.github.stefanrichterhuber.wasmtimejavang.wasip2;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitEnum;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;

/**
 * Direct unit tests for {@link WasiSocketsContext}, wiring its {@code
 * "wasi-io"} dependency by hand (a real {@link WasiIoContext}) and driving
 * the protected two-phase bind/connect/listen methods directly against real
 * loopback {@code java.net} sockets -- covering the option accessors,
 * concurrent accept/connect, and error paths the end-to-end {@code
 * WasmtimeWasiP2Test#wasip2sockettest} fixture (a TCP+UDP client only)
 * doesn't happen to exercise.
 */
public class WasiSocketsContextTest {

    private static WasiSocketsContext newLinkedSockets(WasiIoContext io) {
        WasiSocketsContext sockets = new WasiSocketsContext();
        sockets.onDependenciesResolved(
                (name, version) -> WasiIoContext.NAME.equals(name) ? Optional.of(io) : Optional.empty());
        return sockets;
    }

    private static WitVariant loopback(int port) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("port", port);
        fields.put("address", new Object[] { 127, 0, 0, 1 });
        return new WitVariant("ipv4", fields);
    }

    private static int portOf(WitVariant address) {
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) address.value();
        return (Integer) fields.get("port");
    }

    private static WitResource network(WasiSocketsContext sockets) {
        return (WitResource) sockets.instanceNetwork(null, new Object[0])[0];
    }

    private static WitResource newTcpSocket(WasiSocketsContext sockets) {
        WitResult result = (WitResult) sockets.createTcpSocket(null, new Object[] { new WitEnum("ipv4") })[0];
        assertTrue(result.ok());
        return (WitResource) result.value();
    }

    private static WitResource newUdpSocket(WasiSocketsContext sockets) {
        WitResult result = (WitResult) sockets.createUdpSocket(null, new Object[] { new WitEnum("ipv4") })[0];
        assertTrue(result.ok());
        return (WitResource) result.value();
    }

    private static void bindTcp(WasiSocketsContext sockets, WitResource socket, WitResource net, int port) {
        WitResult bindResult = (WitResult) sockets.tcpStartBind(null, new Object[] { socket, net, loopback(port) })[0];
        assertTrue(bindResult.ok(), "start-bind failed: " + bindResult.value());
        WitResult finishResult = (WitResult) sockets.tcpFinishBind(null, new Object[] { socket })[0];
        assertTrue(finishResult.ok(), "finish-bind failed: " + finishResult.value());
    }

    private static int tcpLocalPort(WasiSocketsContext sockets, WitResource socket) {
        WitResult result = (WitResult) sockets.tcpLocalAddress(null, new Object[] { socket })[0];
        assertTrue(result.ok());
        return portOf((WitVariant) result.value());
    }

    // ---- identity / dependency wiring ----------------------------------------

    @Test
    public void nameProvidedInterfacesAndDependencies() {
        WasiSocketsContext sockets = new WasiSocketsContext();
        assertEquals("wasi-sockets", sockets.name());
        assertEquals(WasiSocketsContext.NAME, sockets.name());
        assertEquals(Set.of("wasi:sockets/network", "wasi:sockets/instance-network",
                "wasi:sockets/tcp-create-socket", "wasi:sockets/tcp", "wasi:sockets/udp-create-socket",
                "wasi:sockets/udp", "wasi:sockets/ip-name-lookup"), sockets.getProvidedInterfaces());
        assertEquals(List.of(WasiIoContext.NAME), sockets.getDependencies());
    }

    @Test
    public void implementsWasmComponentContext() {
        assertTrue(WasmComponentContext.class.isAssignableFrom(WasiSocketsContext.class));
    }

    @Test
    public void onDependenciesResolvedThrowsWhenWasiIoIsMissing() {
        WasiSocketsContext sockets = new WasiSocketsContext();
        ComponentContextLookup emptyLookup = (name, version) -> Optional.empty();
        assertThrows(IllegalStateException.class, () -> sockets.onDependenciesResolved(emptyLookup));
    }

    @Test
    public void importFunctionsAndResourcesCoverEveryDeclaredInterface() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        List<String> funcNames = sockets.getImportFunctions().stream()
                .map(WasmComponentContext.ComponentImportFunction::funcName).toList();
        assertTrue(funcNames.contains("instance-network"));
        assertTrue(funcNames.contains("create-tcp-socket"));
        assertTrue(funcNames.contains("create-udp-socket"));
        assertTrue(funcNames.contains("[method]tcp-socket.accept"));
        assertTrue(funcNames.contains("[method]udp-socket.stream"));
        assertTrue(funcNames.contains("resolve-addresses"));

        List<String> resourceNames = sockets.getImportResources().stream()
                .map(WasmComponentContext.ComponentImportResource::resourceName).toList();
        assertTrue(resourceNames.contains("network"));
        assertTrue(resourceNames.contains("tcp-socket"));
        assertTrue(resourceNames.contains("udp-socket"));
        assertTrue(resourceNames.contains("incoming-datagram-stream"));
        assertTrue(resourceNames.contains("outgoing-datagram-stream"));
        assertTrue(resourceNames.contains("resolve-address-stream"));
    }

    // ---- create-tcp-socket / create-udp-socket -------------------------------

    @Test
    public void createTcpSocketRejectsIpv6() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        Object[] result = sockets.createTcpSocket(null, new Object[] { new WitEnum("ipv6") });
        WitResult wr = (WitResult) result[0];
        assertFalse(wr.ok());
        assertEquals(new WitEnum("not-supported"), wr.value());
    }

    @Test
    public void createUdpSocketRejectsIpv6() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        Object[] result = sockets.createUdpSocket(null, new Object[] { new WitEnum("ipv6") });
        WitResult wr = (WitResult) result[0];
        assertFalse(wr.ok());
        assertEquals(new WitEnum("not-supported"), wr.value());
    }

    // ---- TCP bind / listen / accept / connect --------------------------------

    @Test
    public void tcpBindListenAcceptConnectRoundTripsData() throws Exception {
        WasiIoContext io = new WasiIoContext();
        WasiSocketsContext sockets = newLinkedSockets(io);
        WitResource net = network(sockets);

        WitResource server = newTcpSocket(sockets);
        bindTcp(sockets, server, net, 0);

        WitResult startListen = (WitResult) sockets.tcpStartListen(null, new Object[] { server })[0];
        assertTrue(startListen.ok());
        WitResult finishListen = (WitResult) sockets.tcpFinishListen(null, new Object[] { server })[0];
        assertTrue(finishListen.ok());
        assertTrue((Boolean) sockets.tcpIsListening(null, new Object[] { server })[0]);

        int port = tcpLocalPort(sockets, server);
        assertTrue(port > 0);

        WitResource client = newTcpSocket(sockets);
        CompletableFuture<WitResult> connectFuture = CompletableFuture.supplyAsync(() -> {
            sockets.tcpStartConnect(null, new Object[] { client, net, loopback(port) });
            return (WitResult) sockets.tcpFinishConnect(null, new Object[] { client })[0];
        });

        WitResult acceptResult = (WitResult) sockets.tcpAccept(null, new Object[] { server })[0];
        assertTrue(acceptResult.ok(), "accept failed: " + acceptResult.value());
        Object[] accepted = (Object[]) acceptResult.value();
        WitResource acceptedIn = (WitResource) accepted[1];
        WitResource acceptedOut = (WitResource) accepted[2];

        WitResult connectResult = connectFuture.get(5, TimeUnit.SECONDS);
        assertTrue(connectResult.ok(), "connect failed: " + connectResult.value());
        Object[] clientStreams = (Object[]) connectResult.value();
        WitResource clientIn = (WitResource) clientStreams[0];
        WitResource clientOut = (WitResource) clientStreams[1];

        OutputStream clientOutStream = io.getOutputStream(clientOut.rep());
        clientOutStream.write("ping".getBytes("UTF-8"));
        clientOutStream.flush();
        assertEquals("ping", readNBytes(io.getInputStream(acceptedIn.rep()), 4));

        OutputStream serverOutStream = io.getOutputStream(acceptedOut.rep());
        serverOutStream.write("pong".getBytes("UTF-8"));
        serverOutStream.flush();
        assertEquals("pong", readNBytes(io.getInputStream(clientIn.rep()), 4));
    }

    private static String readNBytes(InputStream in, int n) throws Exception {
        byte[] buf = new byte[n];
        int total = 0;
        while (total < n) {
            int read = in.read(buf, total, n - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return new String(buf, 0, total, "UTF-8");
    }

    @Test
    public void tcpStartBindTwiceFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource net = network(sockets);
        WitResource socket = newTcpSocket(sockets);
        bindTcp(sockets, socket, net, 0);

        WitResult second = (WitResult) sockets.tcpStartBind(null, new Object[] { socket, net, loopback(0) })[0];
        assertFalse(second.ok());
        assertEquals(new WitEnum("invalid-state"), second.value());
    }

    @Test
    public void tcpFinishBindWithoutStartFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        WitResult result = (WitResult) sockets.tcpFinishBind(null, new Object[] { socket })[0];
        assertFalse(result.ok());
        assertEquals(new WitEnum("not-in-progress"), result.value());
    }

    @Test
    public void tcpFinishConnectWithoutStartFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        WitResult result = (WitResult) sockets.tcpFinishConnect(null, new Object[] { socket })[0];
        assertFalse(result.ok());
        assertEquals(new WitEnum("not-in-progress"), result.value());
    }

    @Test
    public void tcpConnectRefusedSurfacesAsError() throws Exception {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource net = network(sockets);
        WitResource client = newTcpSocket(sockets);

        // Bind + immediately close a real socket to obtain a port nothing is
        // listening on.
        int deadPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            deadPort = probe.getLocalPort();
        }

        sockets.tcpStartConnect(null, new Object[] { client, net, loopback(deadPort) });
        WitResult result = (WitResult) sockets.tcpFinishConnect(null, new Object[] { client })[0];
        assertFalse(result.ok());
        assertEquals(new WitEnum("connection-refused"), result.value());
    }

    @Test
    public void tcpAcceptOnNonListeningSocketFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        WitResult result = (WitResult) sockets.tcpAccept(null, new Object[] { socket })[0];
        assertFalse(result.ok());
        assertEquals(new WitEnum("invalid-state"), result.value());
    }

    @Test
    public void tcpLocalAddressBeforeBindFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        WitResult result = (WitResult) sockets.tcpLocalAddress(null, new Object[] { socket })[0];
        assertFalse(result.ok());
        assertEquals(new WitEnum("invalid-state"), result.value());
    }

    @Test
    public void tcpRemoteAddressBeforeConnectFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        WitResult result = (WitResult) sockets.tcpRemoteAddress(null, new Object[] { socket })[0];
        assertFalse(result.ok());
        assertEquals(new WitEnum("invalid-state"), result.value());
    }

    @Test
    public void tcpIsListeningFalseByDefault() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        assertFalse((Boolean) sockets.tcpIsListening(null, new Object[] { socket })[0]);
    }

    @Test
    public void tcpSetListenBacklogSize() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        WitResult result = (WitResult) sockets.tcpSetListenBacklogSize(null, new Object[] { socket, 16L })[0];
        assertTrue(result.ok());
    }

    @Test
    public void tcpKeepAliveEnabledDefaultsFalseAndRoundTrips() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);

        WitResult getBefore = (WitResult) sockets.tcpKeepAliveEnabled(null, new Object[] { socket })[0];
        assertTrue(getBefore.ok());
        assertEquals(Boolean.FALSE, getBefore.value());

        WitResult set = (WitResult) sockets.tcpSetKeepAliveEnabled(null, new Object[] { socket, true })[0];
        assertTrue(set.ok());

        WitResult getAfter = (WitResult) sockets.tcpKeepAliveEnabled(null, new Object[] { socket })[0];
        assertTrue(getAfter.ok());
        assertEquals(Boolean.TRUE, getAfter.value());
    }

    @Test
    public void tcpKeepAliveIdleTimeIntervalCountRoundTrip() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);

        assertTrue(((WitResult) sockets.tcpSetKeepAliveIdleTime(null, new Object[] { socket, 5_000_000_000L })[0])
                .ok());
        assertEquals(5_000_000_000L,
                ((WitResult) sockets.tcpKeepAliveIdleTime(null, new Object[] { socket })[0]).value());

        assertTrue(((WitResult) sockets.tcpSetKeepAliveInterval(null, new Object[] { socket, 1_000_000_000L })[0])
                .ok());
        assertEquals(1_000_000_000L,
                ((WitResult) sockets.tcpKeepAliveInterval(null, new Object[] { socket })[0]).value());

        assertTrue(((WitResult) sockets.tcpSetKeepAliveCount(null, new Object[] { socket, 3 })[0]).ok());
        assertEquals(3, ((WitResult) sockets.tcpKeepAliveCount(null, new Object[] { socket })[0]).value());
    }

    @Test
    public void tcpHopLimitRoundTrips() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        assertTrue(((WitResult) sockets.tcpSetHopLimit(null, new Object[] { socket, 32 })[0]).ok());
        assertEquals(32, ((WitResult) sockets.tcpHopLimit(null, new Object[] { socket })[0]).value());
    }

    @Test
    public void tcpBufferSizesRoundTripBeforeConnect() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);

        assertTrue(((WitResult) sockets.tcpSetReceiveBufferSize(null, new Object[] { socket, 8192L })[0]).ok());
        assertEquals(8192L, ((WitResult) sockets.tcpReceiveBufferSize(null, new Object[] { socket })[0]).value());

        assertTrue(((WitResult) sockets.tcpSetSendBufferSize(null, new Object[] { socket, 4096L })[0]).ok());
        assertEquals(4096L, ((WitResult) sockets.tcpSendBufferSize(null, new Object[] { socket })[0]).value());
    }

    @Test
    public void tcpOptionAccessorsOnUnknownSocketFail() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource bogus = new WitResource(null, 999, true);
        assertFalse(((WitResult) sockets.tcpKeepAliveEnabled(null, new Object[] { bogus })[0]).ok());
        assertFalse(((WitResult) sockets.tcpHopLimit(null, new Object[] { bogus })[0]).ok());
        assertFalse(((WitResult) sockets.tcpReceiveBufferSize(null, new Object[] { bogus })[0]).ok());
        assertFalse(((WitResult) sockets.tcpSendBufferSize(null, new Object[] { bogus })[0]).ok());
        assertFalse((Boolean) sockets.tcpIsListening(null, new Object[] { bogus })[0]);
    }

    @Test
    public void tcpSubscribeReturnsPollableFromSharedIoTable() {
        WasiIoContext io = new WasiIoContext();
        WasiSocketsContext sockets = newLinkedSockets(io);
        WitResource socket = newTcpSocket(sockets);
        Object[] result = sockets.tcpSubscribe(null, new Object[] { socket });
        WitResource pollable = (WitResource) result[0];
        assertEquals("pollable", pollable.resourceName());
        assertEquals(WasiIoResources.ALWAYS_READY, io.getPollableDeadline(pollable.rep()));
    }

    @Test
    public void tcpShutdownOnUnconnectedSocketFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        Object[] result = sockets.tcpShutdown(null, new Object[] { socket, new WitEnum("both") });
        assertFalse(((WitResult) result[0]).ok());
    }

    // ---- UDP ------------------------------------------------------------------

    @Test
    public void udpBindStreamSendReceiveRoundTripsDatagrams() throws Exception {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource net = network(sockets);

        WitResource udpA = newUdpSocket(sockets);
        WitResource udpB = newUdpSocket(sockets);

        assertTrue(((WitResult) sockets.udpStartBind(null, new Object[] { udpA, net, loopback(0) })[0]).ok());
        assertTrue(((WitResult) sockets.udpFinishBind(null, new Object[] { udpA })[0]).ok());
        assertTrue(((WitResult) sockets.udpStartBind(null, new Object[] { udpB, net, loopback(0) })[0]).ok());
        assertTrue(((WitResult) sockets.udpFinishBind(null, new Object[] { udpB })[0]).ok());

        WitResult localA = (WitResult) sockets.udpLocalAddress(null, new Object[] { udpA })[0];
        WitResult localB = (WitResult) sockets.udpLocalAddress(null, new Object[] { udpB })[0];
        assertTrue(localA.ok());
        assertTrue(localB.ok());
        int portA = portOf((WitVariant) localA.value());
        int portB = portOf((WitVariant) localB.value());

        WitResult streamA = (WitResult) sockets.udpStream(null, new Object[] { udpA, Optional.of(loopback(portB)) })[0];
        assertTrue(streamA.ok());
        Object[] streamATuple = (Object[]) streamA.value();
        WitResource outA = (WitResource) streamATuple[1];

        WitResult streamB = (WitResult) sockets.udpStream(null, new Object[] { udpB, Optional.empty() })[0];
        assertTrue(streamB.ok());
        Object[] streamBTuple = (Object[]) streamB.value();
        WitResource inB = (WitResource) streamBTuple[0];

        WitResult remoteA = (WitResult) sockets.udpRemoteAddress(null, new Object[] { udpA })[0];
        assertTrue(remoteA.ok());
        assertEquals(portB, portOf((WitVariant) remoteA.value()));

        Map<String, Object> outgoingDatagram = new LinkedHashMap<>();
        outgoingDatagram.put("data", "hello udp".getBytes("UTF-8"));
        outgoingDatagram.put("remote-address", Optional.<WitVariant>empty());
        WitResult sendResult = (WitResult) sockets.outgoingDatagramSend(null,
                new Object[] { outA, List.of(outgoingDatagram) })[0];
        assertTrue(sendResult.ok(), "send failed: " + sendResult.value());
        assertEquals(1L, sendResult.value());

        WitResult receiveResult = (WitResult) sockets.incomingDatagramReceive(null, new Object[] { inB, 1L })[0];
        assertTrue(receiveResult.ok());
        @SuppressWarnings("unchecked")
        List<Object> received = (List<Object>) receiveResult.value();
        assertEquals(1, received.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> datagram = (Map<String, Object>) received.get(0);
        assertEquals("hello udp", new String((byte[]) datagram.get("data"), "UTF-8"));
        WitVariant remoteAddress = (WitVariant) datagram.get("remote-address");
        assertEquals(portA, portOf(remoteAddress));
    }

    @Test
    public void udpFinishBindWithoutStartFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newUdpSocket(sockets);
        WitResult result = (WitResult) sockets.udpFinishBind(null, new Object[] { socket })[0];
        assertFalse(result.ok());
        assertEquals(new WitEnum("not-in-progress"), result.value());
    }

    @Test
    public void udpStreamBeforeBindFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newUdpSocket(sockets);
        WitResult result = (WitResult) sockets.udpStream(null, new Object[] { socket, Optional.empty() })[0];
        assertFalse(result.ok());
        assertEquals(new WitEnum("invalid-state"), result.value());
    }

    @Test
    public void udpRemoteAddressWithoutConnectedPeerFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource net = network(sockets);
        WitResource socket = newUdpSocket(sockets);
        assertTrue(((WitResult) sockets.udpStartBind(null, new Object[] { socket, net, loopback(0) })[0]).ok());
        assertTrue(((WitResult) sockets.udpFinishBind(null, new Object[] { socket })[0]).ok());

        WitResult result = (WitResult) sockets.udpRemoteAddress(null, new Object[] { socket })[0];
        assertFalse(result.ok());
        assertEquals(new WitEnum("invalid-state"), result.value());
    }

    @Test
    public void udpUnicastHopLimitRoundTrips() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newUdpSocket(sockets);
        assertTrue(((WitResult) sockets.udpSetUnicastHopLimit(null, new Object[] { socket, 16 })[0]).ok());
        assertEquals(16, ((WitResult) sockets.udpUnicastHopLimit(null, new Object[] { socket })[0]).value());
    }

    @Test
    public void udpBufferSizesRoundTripBeforeBind() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newUdpSocket(sockets);
        assertTrue(((WitResult) sockets.udpSetReceiveBufferSize(null, new Object[] { socket, 2048L })[0]).ok());
        assertEquals(2048L, ((WitResult) sockets.udpReceiveBufferSize(null, new Object[] { socket })[0]).value());
        assertTrue(((WitResult) sockets.udpSetSendBufferSize(null, new Object[] { socket, 1024L })[0]).ok());
        assertEquals(1024L, ((WitResult) sockets.udpSendBufferSize(null, new Object[] { socket })[0]).value());
    }

    @Test
    public void udpSubscribeReturnsPollableFromSharedIoTable() {
        WasiIoContext io = new WasiIoContext();
        WasiSocketsContext sockets = newLinkedSockets(io);
        WitResource socket = newUdpSocket(sockets);
        Object[] result = sockets.udpSubscribe(null, new Object[] { socket });
        WitResource pollable = (WitResource) result[0];
        assertEquals(WasiIoResources.ALWAYS_READY, io.getPollableDeadline(pollable.rep()));
    }

    @Test
    public void outgoingDatagramCheckSendReturnsPositiveCapacity() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource net = network(sockets);
        WitResource socket = newUdpSocket(sockets);
        assertTrue(((WitResult) sockets.udpStartBind(null, new Object[] { socket, net, loopback(0) })[0]).ok());
        assertTrue(((WitResult) sockets.udpFinishBind(null, new Object[] { socket })[0]).ok());
        WitResult stream = (WitResult) sockets.udpStream(null, new Object[] { socket, Optional.empty() })[0];
        Object[] tuple = (Object[]) stream.value();
        WitResource out = (WitResource) tuple[1];

        WitResult checkSend = (WitResult) sockets.outgoingDatagramCheckSend(null, new Object[] { out })[0];
        assertTrue(checkSend.ok());
        assertTrue((Long) checkSend.value() > 0);
    }

    @Test
    public void outgoingDatagramSendWithoutTargetOrConnectedPeerFails() throws Exception {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource net = network(sockets);
        WitResource socket = newUdpSocket(sockets);
        assertTrue(((WitResult) sockets.udpStartBind(null, new Object[] { socket, net, loopback(0) })[0]).ok());
        assertTrue(((WitResult) sockets.udpFinishBind(null, new Object[] { socket })[0]).ok());
        WitResult stream = (WitResult) sockets.udpStream(null, new Object[] { socket, Optional.empty() })[0];
        Object[] tuple = (Object[]) stream.value();
        WitResource out = (WitResource) tuple[1];

        Map<String, Object> datagram = new LinkedHashMap<>();
        datagram.put("data", "x".getBytes("UTF-8"));
        datagram.put("remote-address", Optional.<WitVariant>empty());
        WitResult result = (WitResult) sockets.outgoingDatagramSend(null, new Object[] { out, List.of(datagram) })[0];
        assertFalse(result.ok());
        assertEquals(new WitEnum("invalid-argument"), result.value());
    }

    // ---- ip-name-lookup ---------------------------------------------------------

    @Test
    public void resolveAddressesResolvesLoopback() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource net = network(sockets);

        WitResult resolveResult = (WitResult) sockets.resolveAddresses(null, new Object[] { net, "localhost" })[0];
        assertTrue(resolveResult.ok());
        WitResource stream = (WitResource) resolveResult.value();

        boolean foundAny = false;
        while (true) {
            WitResult next = (WitResult) sockets.resolveNextAddress(null, new Object[] { stream })[0];
            assertTrue(next.ok());
            @SuppressWarnings("unchecked")
            Optional<WitVariant> address = (Optional<WitVariant>) next.value();
            if (address.isEmpty()) {
                break;
            }
            foundAny = true;
            assertEquals("ipv4", address.get().caseName());
        }
        assertTrue(foundAny, "expected at least one resolved address for localhost");
    }

    @Test
    public void resolveAddressesOnUnknownHostFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource net = network(sockets);
        WitResult result = (WitResult) sockets.resolveAddresses(null,
                new Object[] { net, "this.host.should.not.resolve.invalid" })[0];
        assertFalse(result.ok());
        assertEquals(new WitEnum("name-unresolvable"), result.value());
    }

    @Test
    public void resolveNextAddressOnUnknownStreamFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResult result = (WitResult) sockets.resolveNextAddress(null,
                new Object[] { new WitResource(null, 999, true) })[0];
        assertFalse(result.ok());
        assertEquals(new WitEnum("invalid-state"), result.value());
    }

    @Test
    public void resolveAddressSubscribeReturnsPollable() {
        WasiIoContext io = new WasiIoContext();
        WasiSocketsContext sockets = newLinkedSockets(io);
        Object[] result = sockets.resolveAddressSubscribe(null, new Object[0]);
        WitResource pollable = (WitResource) result[0];
        assertEquals(WasiIoResources.ALWAYS_READY, io.getPollableDeadline(pollable.rep()));
    }

    // ---- resource destructors ------------------------------------------------

    @Test
    public void dropTcpSocketClosesUnderlyingSocket() throws Exception {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource net = network(sockets);
        WitResource server = newTcpSocket(sockets);
        bindTcp(sockets, server, net, 0);
        assertTrue(((WitResult) sockets.tcpStartListen(null, new Object[] { server })[0]).ok());
        assertTrue(((WitResult) sockets.tcpFinishListen(null, new Object[] { server })[0]).ok());
        int port = tcpLocalPort(sockets, server);

        List<ComponentImportResourceHolder> resources = importResourceHolders(sockets);
        ResourceDestructorLike dropTcpSocket = findDestructor(resources, "wasi:sockets/tcp", "tcp-socket");

        try (Socket ignored = new Socket("127.0.0.1", port)) {
            WitResult acceptResult = (WitResult) sockets.tcpAccept(null, new Object[] { server })[0];
            assertTrue(acceptResult.ok());
        }

        assertDoesNotThrow(() -> dropTcpSocket.drop(server.rep()));
        assertDoesNotThrow(() -> dropTcpSocket.drop(999));
    }

    @Test
    public void dropUdpSocketAndOtherResourcesDoNotThrowOnUnknownReps() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        List<ComponentImportResourceHolder> resources = importResourceHolders(sockets);
        assertDoesNotThrow(() -> findDestructor(resources, "wasi:sockets/udp", "udp-socket").drop(999));
        assertDoesNotThrow(() -> findDestructor(resources, "wasi:sockets/network", "network").drop(999));
        assertDoesNotThrow(
                () -> findDestructor(resources, "wasi:sockets/udp", "incoming-datagram-stream").drop(999));
        assertDoesNotThrow(
                () -> findDestructor(resources, "wasi:sockets/udp", "outgoing-datagram-stream").drop(999));
        assertDoesNotThrow(
                () -> findDestructor(resources, "wasi:sockets/ip-name-lookup", "resolve-address-stream").drop(999));
    }

    private interface ResourceDestructorLike {
        void drop(int rep);
    }

    private interface ComponentImportResourceHolder {
        String interfaceName();

        String resourceName();

        ResourceDestructorLike destructor();
    }

    private static List<ComponentImportResourceHolder> importResourceHolders(WasiSocketsContext sockets) {
        return sockets.getImportResources().stream()
                .<ComponentImportResourceHolder>map(r -> new ComponentImportResourceHolder() {
                    @Override
                    public String interfaceName() {
                        return r.interfaceName();
                    }

                    @Override
                    public String resourceName() {
                        return r.resourceName();
                    }

                    @Override
                    public ResourceDestructorLike destructor() {
                        return r.destructor()::drop;
                    }
                }).toList();
    }

    private static ResourceDestructorLike findDestructor(List<ComponentImportResourceHolder> resources,
            String interfaceNamePrefix, String resourceName) {
        return resources.stream()
                .filter(r -> r.interfaceName().startsWith(interfaceNamePrefix) && r.resourceName().equals(resourceName))
                .findFirst().orElseThrow(() -> new AssertionError("no resource " + resourceName)).destructor();
    }

    // ---- versioning -------------------------------------------------------------

    @Test
    public void versionDefaultsToWasip2StableAndAcceptsRange() {
        WasiSocketsContext sockets = new WasiSocketsContext();
        assertEquals(WasiCliContext.DEFAULT_VERSION, sockets.getVersion());
        assertEquals(new io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion(0, 0, 1),
                sockets.getMiniumVersion());
        assertEquals(new io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion(0, 3, 0),
                sockets.getMaximumVersion());

        io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion newer = io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion
                .parse("0.3.0");
        assertEquals(sockets, sockets.withVersion(newer));
        assertEquals(newer, sockets.getVersion());

        assertThrows(IllegalArgumentException.class,
                () -> sockets.withVersion(new io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion(9, 9, 9)));
    }
}
