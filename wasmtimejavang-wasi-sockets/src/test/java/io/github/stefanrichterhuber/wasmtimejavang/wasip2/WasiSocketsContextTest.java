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
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasicli.WasiCliContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiio.WasiIoContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiio.WasiIoResources;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasisockets.WasiSocketsContext;

/**
 * Direct unit tests for {@link WasiSocketsContext}, wiring its {@code
 * "wasi-io"} dependency by hand (a real {@link WasiIoContext}) and driving
 * the two-phase bind/connect/listen methods directly against real loopback
 * {@code java.net} sockets -- covering the option accessors, concurrent
 * accept/connect, and error paths the end-to-end {@code
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
        return sockets.instanceNetworkInstanceNetwork(null);
    }

    private static WitResource newTcpSocket(WasiSocketsContext sockets) {
        WitResult result = sockets.tcpCreateSocketCreateTcpSocket(null, new WitEnum("ipv4"));
        assertTrue(result.ok());
        return (WitResource) result.value();
    }

    private static WitResource newUdpSocket(WasiSocketsContext sockets) {
        WitResult result = sockets.udpCreateSocketCreateUdpSocket(null, new WitEnum("ipv4"));
        assertTrue(result.ok());
        return (WitResource) result.value();
    }

    private static void bindTcp(WasiSocketsContext sockets, WitResource socket, WitResource net, int port) {
        WitResult bindResult = sockets.tcpSocketStartBind(null, socket, net, loopback(port));
        assertTrue(bindResult.ok(), "start-bind failed: " + bindResult.value());
        WitResult finishResult = sockets.tcpSocketFinishBind(null, socket);
        assertTrue(finishResult.ok(), "finish-bind failed: " + finishResult.value());
    }

    private static int tcpLocalPort(WasiSocketsContext sockets, WitResource socket) {
        WitResult result = sockets.tcpSocketLocalAddress(null, socket);
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
        assertTrue(funcNames.contains("[method]tcp-socket.address-family"));
        assertTrue(funcNames.contains("[method]udp-socket.stream"));
        assertTrue(funcNames.contains("[method]udp-socket.address-family"));
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
        WitResult wr = sockets.tcpCreateSocketCreateTcpSocket(null, new WitEnum("ipv6"));
        assertFalse(wr.ok());
        assertEquals(new WitEnum("not-supported"), wr.value());
    }

    @Test
    public void createUdpSocketRejectsIpv6() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResult wr = sockets.udpCreateSocketCreateUdpSocket(null, new WitEnum("ipv6"));
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

        WitResult startListen = sockets.tcpSocketStartListen(null, server);
        assertTrue(startListen.ok());
        WitResult finishListen = sockets.tcpSocketFinishListen(null, server);
        assertTrue(finishListen.ok());
        assertTrue(sockets.tcpSocketIsListening(null, server));

        int port = tcpLocalPort(sockets, server);
        assertTrue(port > 0);

        WitResource client = newTcpSocket(sockets);
        CompletableFuture<WitResult> connectFuture = CompletableFuture.supplyAsync(() -> {
            sockets.tcpSocketStartConnect(null, client, net, loopback(port));
            return sockets.tcpSocketFinishConnect(null, client);
        });

        WitResult acceptResult = sockets.tcpSocketAccept(null, server);
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

        WitResult second = sockets.tcpSocketStartBind(null, socket, net, loopback(0));
        assertFalse(second.ok());
        assertEquals(new WitEnum("invalid-state"), second.value());
    }

    @Test
    public void tcpFinishBindWithoutStartFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        WitResult result = sockets.tcpSocketFinishBind(null, socket);
        assertFalse(result.ok());
        assertEquals(new WitEnum("not-in-progress"), result.value());
    }

    @Test
    public void tcpFinishConnectWithoutStartFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        WitResult result = sockets.tcpSocketFinishConnect(null, socket);
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

        sockets.tcpSocketStartConnect(null, client, net, loopback(deadPort));
        WitResult result = sockets.tcpSocketFinishConnect(null, client);
        assertFalse(result.ok());
        assertEquals(new WitEnum("connection-refused"), result.value());
    }

    @Test
    public void tcpAcceptOnNonListeningSocketFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        WitResult result = sockets.tcpSocketAccept(null, socket);
        assertFalse(result.ok());
        assertEquals(new WitEnum("invalid-state"), result.value());
    }

    @Test
    public void tcpLocalAddressBeforeBindFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        WitResult result = sockets.tcpSocketLocalAddress(null, socket);
        assertFalse(result.ok());
        assertEquals(new WitEnum("invalid-state"), result.value());
    }

    @Test
    public void tcpRemoteAddressBeforeConnectFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        WitResult result = sockets.tcpSocketRemoteAddress(null, socket);
        assertFalse(result.ok());
        assertEquals(new WitEnum("invalid-state"), result.value());
    }

    @Test
    public void tcpIsListeningFalseByDefault() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        assertFalse(sockets.tcpSocketIsListening(null, socket));
    }

    @Test
    public void tcpAddressFamilyIsAlwaysIpv4() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        assertEquals(new WitEnum("ipv4"), sockets.tcpSocketAddressFamily(null, socket));
    }

    @Test
    public void tcpSetListenBacklogSize() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        WitResult result = sockets.tcpSocketSetListenBacklogSize(null, socket, 16L);
        assertTrue(result.ok());
    }

    @Test
    public void tcpKeepAliveEnabledDefaultsFalseAndRoundTrips() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);

        WitResult getBefore = sockets.tcpSocketKeepAliveEnabled(null, socket);
        assertTrue(getBefore.ok());
        assertEquals(Boolean.FALSE, getBefore.value());

        WitResult set = sockets.tcpSocketSetKeepAliveEnabled(null, socket, true);
        assertTrue(set.ok());

        WitResult getAfter = sockets.tcpSocketKeepAliveEnabled(null, socket);
        assertTrue(getAfter.ok());
        assertEquals(Boolean.TRUE, getAfter.value());
    }

    @Test
    public void tcpKeepAliveIdleTimeIntervalCountRoundTrip() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);

        assertTrue(sockets.tcpSocketSetKeepAliveIdleTime(null, socket, 5_000_000_000L).ok());
        assertEquals(5_000_000_000L, sockets.tcpSocketKeepAliveIdleTime(null, socket).value());

        assertTrue(sockets.tcpSocketSetKeepAliveInterval(null, socket, 1_000_000_000L).ok());
        assertEquals(1_000_000_000L, sockets.tcpSocketKeepAliveInterval(null, socket).value());

        assertTrue(sockets.tcpSocketSetKeepAliveCount(null, socket, 3).ok());
        assertEquals(3, sockets.tcpSocketKeepAliveCount(null, socket).value());
    }

    @Test
    public void tcpHopLimitRoundTrips() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        assertTrue(sockets.tcpSocketSetHopLimit(null, socket, 32).ok());
        assertEquals(32, sockets.tcpSocketHopLimit(null, socket).value());
    }

    @Test
    public void tcpBufferSizesRoundTripBeforeConnect() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);

        assertTrue(sockets.tcpSocketSetReceiveBufferSize(null, socket, 8192L).ok());
        assertEquals(8192L, sockets.tcpSocketReceiveBufferSize(null, socket).value());

        assertTrue(sockets.tcpSocketSetSendBufferSize(null, socket, 4096L).ok());
        assertEquals(4096L, sockets.tcpSocketSendBufferSize(null, socket).value());
    }

    @Test
    public void tcpOptionAccessorsOnUnknownSocketFail() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource bogus = new WitResource(null, 999, true);
        assertFalse(sockets.tcpSocketKeepAliveEnabled(null, bogus).ok());
        assertFalse(sockets.tcpSocketHopLimit(null, bogus).ok());
        assertFalse(sockets.tcpSocketReceiveBufferSize(null, bogus).ok());
        assertFalse(sockets.tcpSocketSendBufferSize(null, bogus).ok());
        assertFalse(sockets.tcpSocketIsListening(null, bogus));
    }

    @Test
    public void tcpSubscribeReturnsPollableFromSharedIoTable() {
        WasiIoContext io = new WasiIoContext();
        WasiSocketsContext sockets = newLinkedSockets(io);
        WitResource socket = newTcpSocket(sockets);
        WitResource pollable = sockets.tcpSocketSubscribe(null, socket);
        assertEquals("pollable", pollable.resourceName());
        assertEquals(WasiIoResources.ALWAYS_READY, io.getPollableDeadline(pollable.rep()));
    }

    @Test
    public void tcpShutdownOnUnconnectedSocketFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newTcpSocket(sockets);
        WitResult result = sockets.tcpSocketShutdown(null, socket, new WitEnum("both"));
        assertFalse(result.ok());
    }

    // ---- UDP ------------------------------------------------------------------

    @Test
    public void udpBindStreamSendReceiveRoundTripsDatagrams() throws Exception {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource net = network(sockets);

        WitResource udpA = newUdpSocket(sockets);
        WitResource udpB = newUdpSocket(sockets);

        assertTrue(sockets.udpSocketStartBind(null, udpA, net, loopback(0)).ok());
        assertTrue(sockets.udpSocketFinishBind(null, udpA).ok());
        assertTrue(sockets.udpSocketStartBind(null, udpB, net, loopback(0)).ok());
        assertTrue(sockets.udpSocketFinishBind(null, udpB).ok());

        WitResult localA = sockets.udpSocketLocalAddress(null, udpA);
        WitResult localB = sockets.udpSocketLocalAddress(null, udpB);
        assertTrue(localA.ok());
        assertTrue(localB.ok());
        int portA = portOf((WitVariant) localA.value());
        int portB = portOf((WitVariant) localB.value());

        WitResult streamA = sockets.udpSocketStream(null, udpA, Optional.of(loopback(portB)));
        assertTrue(streamA.ok());
        Object[] streamATuple = (Object[]) streamA.value();
        WitResource outA = (WitResource) streamATuple[1];

        WitResult streamB = sockets.udpSocketStream(null, udpB, Optional.empty());
        assertTrue(streamB.ok());
        Object[] streamBTuple = (Object[]) streamB.value();
        WitResource inB = (WitResource) streamBTuple[0];

        WitResult remoteA = sockets.udpSocketRemoteAddress(null, udpA);
        assertTrue(remoteA.ok());
        assertEquals(portB, portOf((WitVariant) remoteA.value()));

        Map<String, Object> outgoingDatagram = new LinkedHashMap<>();
        outgoingDatagram.put("data", "hello udp".getBytes("UTF-8"));
        outgoingDatagram.put("remote-address", Optional.<WitVariant>empty());
        WitResult sendResult = sockets.outgoingDatagramStreamSend(null, outA, List.of(outgoingDatagram));
        assertTrue(sendResult.ok(), "send failed: " + sendResult.value());
        assertEquals(1L, sendResult.value());

        WitResult receiveResult = sockets.incomingDatagramStreamReceive(null, inB, 1L);
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
        WitResult result = sockets.udpSocketFinishBind(null, socket);
        assertFalse(result.ok());
        assertEquals(new WitEnum("not-in-progress"), result.value());
    }

    @Test
    public void udpStreamBeforeBindFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newUdpSocket(sockets);
        WitResult result = sockets.udpSocketStream(null, socket, Optional.empty());
        assertFalse(result.ok());
        assertEquals(new WitEnum("invalid-state"), result.value());
    }

    @Test
    public void udpRemoteAddressWithoutConnectedPeerFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource net = network(sockets);
        WitResource socket = newUdpSocket(sockets);
        assertTrue(sockets.udpSocketStartBind(null, socket, net, loopback(0)).ok());
        assertTrue(sockets.udpSocketFinishBind(null, socket).ok());

        WitResult result = sockets.udpSocketRemoteAddress(null, socket);
        assertFalse(result.ok());
        assertEquals(new WitEnum("invalid-state"), result.value());
    }

    @Test
    public void udpAddressFamilyIsAlwaysIpv4() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newUdpSocket(sockets);
        assertEquals(new WitEnum("ipv4"), sockets.udpSocketAddressFamily(null, socket));
    }

    @Test
    public void udpUnicastHopLimitRoundTrips() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newUdpSocket(sockets);
        assertTrue(sockets.udpSocketSetUnicastHopLimit(null, socket, 16).ok());
        assertEquals(16, sockets.udpSocketUnicastHopLimit(null, socket).value());
    }

    @Test
    public void udpBufferSizesRoundTripBeforeBind() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource socket = newUdpSocket(sockets);
        assertTrue(sockets.udpSocketSetReceiveBufferSize(null, socket, 2048L).ok());
        assertEquals(2048L, sockets.udpSocketReceiveBufferSize(null, socket).value());
        assertTrue(sockets.udpSocketSetSendBufferSize(null, socket, 1024L).ok());
        assertEquals(1024L, sockets.udpSocketSendBufferSize(null, socket).value());
    }

    @Test
    public void udpSubscribeReturnsPollableFromSharedIoTable() {
        WasiIoContext io = new WasiIoContext();
        WasiSocketsContext sockets = newLinkedSockets(io);
        WitResource socket = newUdpSocket(sockets);
        WitResource pollable = sockets.udpSocketSubscribe(null, socket);
        assertEquals(WasiIoResources.ALWAYS_READY, io.getPollableDeadline(pollable.rep()));
    }

    @Test
    public void outgoingDatagramCheckSendReturnsPositiveCapacity() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource net = network(sockets);
        WitResource socket = newUdpSocket(sockets);
        assertTrue(sockets.udpSocketStartBind(null, socket, net, loopback(0)).ok());
        assertTrue(sockets.udpSocketFinishBind(null, socket).ok());
        WitResult stream = sockets.udpSocketStream(null, socket, Optional.empty());
        Object[] tuple = (Object[]) stream.value();
        WitResource out = (WitResource) tuple[1];

        WitResult checkSend = sockets.outgoingDatagramStreamCheckSend(null, out);
        assertTrue(checkSend.ok());
        assertTrue((Long) checkSend.value() > 0);
    }

    @Test
    public void outgoingDatagramSendWithoutTargetOrConnectedPeerFails() throws Exception {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource net = network(sockets);
        WitResource socket = newUdpSocket(sockets);
        assertTrue(sockets.udpSocketStartBind(null, socket, net, loopback(0)).ok());
        assertTrue(sockets.udpSocketFinishBind(null, socket).ok());
        WitResult stream = sockets.udpSocketStream(null, socket, Optional.empty());
        Object[] tuple = (Object[]) stream.value();
        WitResource out = (WitResource) tuple[1];

        Map<String, Object> datagram = new LinkedHashMap<>();
        datagram.put("data", "x".getBytes("UTF-8"));
        datagram.put("remote-address", Optional.<WitVariant>empty());
        WitResult result = sockets.outgoingDatagramStreamSend(null, out, List.of(datagram));
        assertFalse(result.ok());
        assertEquals(new WitEnum("invalid-argument"), result.value());
    }

    // ---- ip-name-lookup ---------------------------------------------------------

    @Test
    public void resolveAddressesResolvesLoopback() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource net = network(sockets);

        WitResult resolveResult = sockets.ipNameLookupResolveAddresses(null, net, "localhost");
        assertTrue(resolveResult.ok());
        WitResource stream = (WitResource) resolveResult.value();

        boolean foundAny = false;
        while (true) {
            WitResult next = sockets.resolveAddressStreamResolveNextAddress(null, stream);
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
        WitResult result = sockets.ipNameLookupResolveAddresses(null, net, "this.host.should.not.resolve.invalid");
        assertFalse(result.ok());
        assertEquals(new WitEnum("name-unresolvable"), result.value());
    }

    @Test
    public void resolveNextAddressOnUnknownStreamFails() {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResult result = sockets.resolveAddressStreamResolveNextAddress(null, new WitResource(null, 999, true));
        assertFalse(result.ok());
        assertEquals(new WitEnum("invalid-state"), result.value());
    }

    @Test
    public void resolveAddressSubscribeReturnsPollable() {
        WasiIoContext io = new WasiIoContext();
        WasiSocketsContext sockets = newLinkedSockets(io);
        WitResource pollable = sockets.resolveAddressStreamSubscribe(null, new WitResource(null, 1, true));
        assertEquals(WasiIoResources.ALWAYS_READY, io.getPollableDeadline(pollable.rep()));
    }

    // ---- resource destructors ------------------------------------------------

    @Test
    public void dropTcpSocketClosesUnderlyingSocket() throws Exception {
        WasiSocketsContext sockets = newLinkedSockets(new WasiIoContext());
        WitResource net = network(sockets);
        WitResource server = newTcpSocket(sockets);
        bindTcp(sockets, server, net, 0);
        assertTrue(sockets.tcpSocketStartListen(null, server).ok());
        assertTrue(sockets.tcpSocketFinishListen(null, server).ok());
        int port = tcpLocalPort(sockets, server);

        List<ComponentImportResourceHolder> resources = importResourceHolders(sockets);
        ResourceDestructorLike dropTcpSocket = findDestructor(resources, "wasi:sockets/tcp", "tcp-socket");

        try (Socket ignored = new Socket("127.0.0.1", port)) {
            WitResult acceptResult = sockets.tcpSocketAccept(null, server);
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
