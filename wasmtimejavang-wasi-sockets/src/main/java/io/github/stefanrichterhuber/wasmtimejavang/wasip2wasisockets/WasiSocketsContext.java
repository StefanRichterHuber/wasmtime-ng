package io.github.stefanrichterhuber.wasmtimejavang.wasip2wasisockets;

import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup;
import io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitEnum;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasisockets.InstanceNetworkContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasisockets.IpNameLookupContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasisockets.NetworkContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasisockets.TcpContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasisockets.TcpCreateSocketContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasisockets.UdpContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasisockets.UdpCreateSocketContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiio.WasiIoContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiio.WasiIoResources;

/**
 * Implementation of {@code wasi:sockets/network}, {@code instance-network},
 * {@code tcp-create-socket}, {@code tcp}, {@code udp-create-socket},
 * {@code udp} and {@code ip-name-lookup} (WASI Preview 2, 0.2.6) -- the
 * {@code "wasi-sockets"} component context.
 * <br>
 * Implements all seven generated interfaces at once.
 * <br>
 * Unlike WASI Preview 1's socket support ({@code SocketWasiFileDescriptor}/
 * {@code ServerSocketWasiFileDescriptor}, which only ever wrap a socket the
 * <em>host</em> already created and preopened), a wasi:sockets guest drives
 * the entire socket lifecycle itself -- create, bind, connect/listen,
 * accept, send/receive -- so there is little to reuse from p1 beyond the
 * general idea of delegating to {@code java.net}.
 * <br>
 * The two-phase {@code start-x}/{@code finish-x} operations WASI defines for
 * non-blocking bind/connect/listen are collapsed here: since every host call
 * in this bridge is inherently synchronous from the guest's perspective
 * anyway (a JNI callback runs to completion before control returns to wasm),
 * {@code start-x} performs the real (blocking) {@code java.net} operation
 * immediately and remembers the outcome, and {@code finish-x} just reports
 * it -- there is no actual asynchrony to preserve.
 * <br>
 * IPv4 only: an {@code ipv6} {@code address-family} is rejected with
 * {@code not-supported} at socket creation, and {@code [method]tcp-socket
 * /udp-socket.address-family} always reports a fixed {@code ipv4}. Several
 * options WASI exposes that {@code java.net} has no equivalent for (TCP
 * {@code hop-limit} and {@code keep-alive-idle-time}/{@code interval}/
 * {@code count}, UDP {@code unicast-hop-limit}) are stored and returned as
 * configured but not actually applied to the OS socket -- documented per-
 * method below.
 * <br>
 * Depends on {@code "wasi-io"} ({@link WasiIoResources}): TCP connection
 * streams are registered in -- and pollables are always allocated from --
 * the same shared tables {@code wasi:io/streams}/{@code wasi:io/poll}
 * operate on, since {@code [method]pollable.block} and the free {@code poll}
 * function (both implemented by {@link WasiIoContext}) only ever look a
 * pollable up in that one shared table.
 */
public class WasiSocketsContext implements NetworkContext, InstanceNetworkContext, TcpCreateSocketContext,
        TcpContext, UdpCreateSocketContext, UdpContext, IpNameLookupContext {

    /** The stable name other contexts reference via {@code getDependencies()}. */
    public static final String NAME = "wasi-sockets";

    /** How long {@code start-connect} blocks waiting for a TCP handshake. */
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    private SemanticVersion version = DEFAULT_VERSION;
    private WasiIoResources io;

    private final Map<Integer, Object> networks = new ConcurrentHashMap<>();
    private final Map<Integer, TcpSocketEntry> tcpSockets = new ConcurrentHashMap<>();
    private final Map<Integer, UdpSocketEntry> udpSockets = new ConcurrentHashMap<>();
    private final Map<Integer, UdpSocketEntry> incomingDatagramStreams = new ConcurrentHashMap<>();
    private final Map<Integer, UdpSocketEntry> outgoingDatagramStreams = new ConcurrentHashMap<>();
    private final Map<Integer, Iterator<InetAddress>> resolveStreams = new ConcurrentHashMap<>();
    private final AtomicInteger nextRep = new AtomicInteger(1);

    private static final class TcpSocketEntry {
        volatile InetSocketAddress localAddress;
        volatile boolean bound;
        volatile boolean listening;
        volatile ServerSocket serverSocket;
        volatile Socket socket;
        volatile String pendingError;
        volatile long backlog = 128;
        volatile Boolean pendingKeepAlive;
        volatile long keepAliveIdleTimeNanos = 7_200_000_000_000L;
        volatile long keepAliveIntervalNanos = 75_000_000_000L;
        volatile long keepAliveCount = 9;
        volatile short hopLimit = 64;
        volatile Long pendingReceiveBufferSize;
        volatile Long pendingSendBufferSize;
    }

    private static final class UdpSocketEntry {
        volatile DatagramSocket socket;
        volatile InetSocketAddress pendingLocalAddress;
        volatile boolean bound;
        volatile InetSocketAddress connectedRemote;
        volatile short hopLimit = 64;
        volatile Long pendingReceiveBufferSize;
        volatile Long pendingSendBufferSize;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> getProvidedInterfaces() {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(NetworkContext.super.getProvidedInterfaces());
        result.addAll(InstanceNetworkContext.super.getProvidedInterfaces());
        result.addAll(TcpCreateSocketContext.super.getProvidedInterfaces());
        result.addAll(TcpContext.super.getProvidedInterfaces());
        result.addAll(UdpCreateSocketContext.super.getProvidedInterfaces());
        result.addAll(UdpContext.super.getProvidedInterfaces());
        result.addAll(IpNameLookupContext.super.getProvidedInterfaces());
        return result;
    }

    @Override
    public List<String> getDependencies() {
        return List.of(WasiIoContext.NAME);
    }

    @Override
    public void onDependenciesResolved(ComponentContextLookup lookup) {
        this.io = (WasiIoResources) lookup.resolve(WasiIoContext.NAME, getVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "\"" + NAME + "\" requires a \"" + WasiIoContext.NAME + "\" dependency implementing "
                                + WasiIoResources.class.getSimpleName()));
    }

    @Override
    public List<ComponentImportFunction> getImportFunctions() {
        List<ComponentImportFunction> result = new ArrayList<>();
        result.addAll(NetworkContext.super.getImportFunctions());
        result.addAll(InstanceNetworkContext.super.getImportFunctions());
        result.addAll(TcpCreateSocketContext.super.getImportFunctions());
        result.addAll(TcpContext.super.getImportFunctions());
        result.addAll(UdpCreateSocketContext.super.getImportFunctions());
        result.addAll(UdpContext.super.getImportFunctions());
        result.addAll(IpNameLookupContext.super.getImportFunctions());
        return result;
    }

    @Override
    public List<ComponentImportResource> getImportResources() {
        List<ComponentImportResource> result = new ArrayList<>();
        result.addAll(NetworkContext.super.getImportResources());
        result.addAll(InstanceNetworkContext.super.getImportResources());
        result.addAll(TcpCreateSocketContext.super.getImportResources());
        result.addAll(TcpContext.super.getImportResources());
        result.addAll(UdpCreateSocketContext.super.getImportResources());
        result.addAll(UdpContext.super.getImportResources());
        result.addAll(IpNameLookupContext.super.getImportResources());
        return result;
    }

    @Override
    public void dropNetwork(int rep) {
        networks.remove(rep);
    }

    @Override
    public void dropTcpSocket(int rep) {
        TcpSocketEntry entry = tcpSockets.remove(rep);
        if (entry != null) {
            closeQuietly(entry.serverSocket);
            closeQuietly(entry.socket);
        }
    }

    @Override
    public void dropUdpSocket(int rep) {
        UdpSocketEntry entry = udpSockets.remove(rep);
        if (entry != null) {
            closeQuietly(entry.socket);
        }
    }

    @Override
    public void dropIncomingDatagramStream(int rep) {
        incomingDatagramStreams.remove(rep);
    }

    @Override
    public void dropOutgoingDatagramStream(int rep) {
        outgoingDatagramStreams.remove(rep);
    }

    @Override
    public void dropResolveAddressStream(int rep) {
        resolveStreams.remove(rep);
    }

    @Override
    public void dropPollable(int rep) {
        io.dropPollable(rep);
    }

    @Override
    public void dropInputStream(int rep) {
        io.dropInputStream(rep);
    }

    @Override
    public void dropOutputStream(int rep) {
        io.dropOutputStream(rep);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // Best-effort close, mirrors WasiIoContext#dropOutputStream.
            }
        }
    }

    private static WitResult okResult(Object value) {
        return WitResult.ok(value);
    }

    private static WitResult errorResult(String errorCode) {
        return WitResult.err(new WitEnum(errorCode));
    }

    /** Maps a {@code java.net} exception to the closest {@code error-code} case. */
    private static String mapException(Exception e) {
        if (e instanceof BindException) {
            return "address-in-use";
        }
        if (e instanceof ConnectException) {
            return "connection-refused";
        }
        if (e instanceof NoRouteToHostException) {
            return "remote-unreachable";
        }
        if (e instanceof SocketTimeoutException) {
            return "timeout";
        }
        if (e instanceof UnknownHostException) {
            return "name-unresolvable";
        }
        return "unknown";
    }

    // ---- address conversion ------------------------------------------------

    @SuppressWarnings("unchecked")
    private static InetSocketAddress toInetSocketAddress(WitVariant address) {
        Map<String, Object> fields = (Map<String, Object>) address.value();
        int port = (Integer) fields.get("port");
        Object[] octetsOrGroups = (Object[]) fields.get("address");
        InetAddress inetAddress = "ipv6".equals(address.caseName())
                ? ipv6GroupsToInetAddress(octetsOrGroups)
                : ipv4OctetsToInetAddress(octetsOrGroups);
        return new InetSocketAddress(inetAddress, port);
    }

    private static InetAddress ipv4OctetsToInetAddress(Object[] octets) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = (byte) (int) (Integer) octets[i];
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Malformed IPv4 address", e);
        }
    }

    private static InetAddress ipv6GroupsToInetAddress(Object[] groups) {
        byte[] bytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            int group = (Integer) groups[i];
            bytes[i * 2] = (byte) (group >> 8);
            bytes[i * 2 + 1] = (byte) group;
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Malformed IPv6 address", e);
        }
    }

    private static WitVariant toWitIpSocketAddress(InetSocketAddress address) {
        InetAddress inetAddress = address.getAddress();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("port", address.getPort());
        if (inetAddress instanceof Inet6Address) {
            fields.put("flow-info", 0);
            fields.put("address", ipv6Groups(inetAddress));
            fields.put("scope-id", 0);
            return new WitVariant("ipv6", fields);
        }
        fields.put("address", ipv4Octets(inetAddress));
        return new WitVariant("ipv4", fields);
    }

    private static WitVariant toWitIpAddress(InetAddress inetAddress) {
        if (inetAddress instanceof Inet6Address) {
            return new WitVariant("ipv6", ipv6Groups(inetAddress));
        }
        return new WitVariant("ipv4", ipv4Octets(inetAddress));
    }

    private static Object[] ipv4Octets(InetAddress inetAddress) {
        byte[] bytes = inetAddress.getAddress();
        Object[] octets = new Object[4];
        for (int i = 0; i < 4; i++) {
            octets[i] = bytes[i] & 0xFF;
        }
        return octets;
    }

    private static Object[] ipv6Groups(InetAddress inetAddress) {
        byte[] bytes = inetAddress.getAddress();
        Object[] groups = new Object[8];
        for (int i = 0; i < 8; i++) {
            groups[i] = ((bytes[i * 2] & 0xFF) << 8) | (bytes[i * 2 + 1] & 0xFF);
        }
        return groups;
    }

    // ---- instance-network / tcp-create-socket / udp-create-socket ---------

    @Override
    public WitResource instanceNetworkInstanceNetwork(WasmtimeComponentInstance instance) {
        int rep = nextRep.getAndIncrement();
        networks.put(rep, Boolean.TRUE);
        return WitResource.own("network", rep);
    }

    @Override
    public WitResult tcpCreateSocketCreateTcpSocket(WasmtimeComponentInstance instance, WitEnum addressFamily) {
        if (!"ipv4".equals(addressFamily.name())) {
            return errorResult("not-supported");
        }
        int rep = nextRep.getAndIncrement();
        tcpSockets.put(rep, new TcpSocketEntry());
        return okResult(WitResource.own("tcp-socket", rep));
    }

    @Override
    public WitResult udpCreateSocketCreateUdpSocket(WasmtimeComponentInstance instance, WitEnum addressFamily) {
        if (!"ipv4".equals(addressFamily.name())) {
            return errorResult("not-supported");
        }
        int rep = nextRep.getAndIncrement();
        udpSockets.put(rep, new UdpSocketEntry());
        return okResult(WitResource.own("udp-socket", rep));
    }

    // ---- wasi:sockets/tcp ---------------------------------------------------

    @Override
    public WitResult tcpSocketStartBind(WasmtimeComponentInstance instance, WitResource self, WitResource network,
            WitVariant localAddress) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        if (entry.bound) {
            return errorResult("invalid-state");
        }
        entry.localAddress = toInetSocketAddress(localAddress);
        entry.bound = true;
        return okResult(null);
    }

    @Override
    public WitResult tcpSocketFinishBind(WasmtimeComponentInstance instance, WitResource self) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null || !entry.bound) {
            return errorResult("not-in-progress");
        }
        return okResult(null);
    }

    @Override
    public WitResult tcpSocketStartConnect(WasmtimeComponentInstance instance, WitResource self, WitResource network,
            WitVariant remoteAddress) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        InetSocketAddress remote = toInetSocketAddress(remoteAddress);
        try {
            Socket socket = new Socket();
            if (entry.localAddress != null) {
                socket.bind(entry.localAddress);
            }
            socket.connect(remote, CONNECT_TIMEOUT_MILLIS);
            applyPendingTcpOptions(entry, socket);
            entry.socket = socket;
            entry.pendingError = null;
        } catch (IOException e) {
            entry.pendingError = mapException(e);
        }
        return okResult(null);
    }

    @Override
    public WitResult tcpSocketFinishConnect(WasmtimeComponentInstance instance, WitResource self) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        if (entry.pendingError != null) {
            String error = entry.pendingError;
            entry.pendingError = null;
            return errorResult(error);
        }
        if (entry.socket == null) {
            return errorResult("not-in-progress");
        }
        return okResult(registerTcpStreams(entry.socket));
    }

    @Override
    public WitResult tcpSocketStartListen(WasmtimeComponentInstance instance, WitResource self) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null || !entry.bound) {
            return errorResult("invalid-state");
        }
        try (ServerSocket serverSocket = new ServerSocket();) {

            serverSocket.bind(entry.localAddress, (int) entry.backlog);
            entry.serverSocket = serverSocket;
            entry.pendingError = null;
        } catch (IOException e) {
            entry.pendingError = mapException(e);
        }
        return okResult(null);
    }

    @Override
    public WitResult tcpSocketFinishListen(WasmtimeComponentInstance instance, WitResource self) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        if (entry.pendingError != null) {
            String error = entry.pendingError;
            entry.pendingError = null;
            return errorResult(error);
        }
        if (entry.serverSocket == null) {
            return errorResult("not-in-progress");
        }
        entry.listening = true;
        entry.localAddress = (InetSocketAddress) entry.serverSocket.getLocalSocketAddress();
        return okResult(null);
    }

    @Override
    public WitResult tcpSocketAccept(WasmtimeComponentInstance instance, WitResource self) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null || !entry.listening || entry.serverSocket == null) {
            return errorResult("invalid-state");
        }
        try {
            Socket client = entry.serverSocket.accept();
            applyPendingTcpOptions(entry, client);
            TcpSocketEntry clientEntry = new TcpSocketEntry();
            clientEntry.socket = client;
            clientEntry.bound = true;
            clientEntry.localAddress = (InetSocketAddress) client.getLocalSocketAddress();
            int clientRep = nextRep.getAndIncrement();
            tcpSockets.put(clientRep, clientEntry);

            Object[] streams = registerTcpStreams(client);
            Object[] tuple = new Object[] { WitResource.own("tcp-socket", clientRep), streams[0], streams[1] };
            return okResult(tuple);
        } catch (IOException e) {
            return errorResult(mapException(e));
        }
    }

    private Object[] registerTcpStreams(Socket socket) {
        try {
            int inRep = io.registerInputStream(socket.getInputStream());
            int outRep = io.registerOutputStream(socket.getOutputStream());
            return new Object[] { WitResource.own("input-stream", inRep), WitResource.own("output-stream", outRep) };
        } catch (IOException e) {
            throw new IllegalStateException("Failed to obtain socket streams", e);
        }
    }

    private static void applyPendingTcpOptions(TcpSocketEntry entry, Socket socket) throws SocketException {
        if (entry.pendingKeepAlive != null) {
            socket.setKeepAlive(entry.pendingKeepAlive);
        }
        if (entry.pendingReceiveBufferSize != null) {
            socket.setReceiveBufferSize(entry.pendingReceiveBufferSize.intValue());
        }
        if (entry.pendingSendBufferSize != null) {
            socket.setSendBufferSize(entry.pendingSendBufferSize.intValue());
        }
    }

    @Override
    public WitResult tcpSocketLocalAddress(WasmtimeComponentInstance instance, WitResource self) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        InetSocketAddress address = entry.serverSocket != null
                ? (InetSocketAddress) entry.serverSocket.getLocalSocketAddress()
                : (entry.socket != null ? (InetSocketAddress) entry.socket.getLocalSocketAddress()
                        : entry.localAddress);
        if (address == null) {
            return errorResult("invalid-state");
        }
        return okResult(toWitIpSocketAddress(address));
    }

    @Override
    public WitResult tcpSocketRemoteAddress(WasmtimeComponentInstance instance, WitResource self) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null || entry.socket == null) {
            return errorResult("invalid-state");
        }
        return okResult(toWitIpSocketAddress((InetSocketAddress) entry.socket.getRemoteSocketAddress()));
    }

    @Override
    public boolean tcpSocketIsListening(WasmtimeComponentInstance instance, WitResource self) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        return entry != null && entry.listening;
    }

    /**
     * IPv4 only (see class javadoc), so this always reports a fixed {@code
     * ipv4}.
     */
    @Override
    public WitEnum tcpSocketAddressFamily(WasmtimeComponentInstance instance, WitResource self) {
        return new WitEnum("ipv4");
    }

    @Override
    public WitResult tcpSocketSetListenBacklogSize(WasmtimeComponentInstance instance, WitResource self, long value) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.backlog = value;
        return okResult(null);
    }

    @Override
    public WitResult tcpSocketKeepAliveEnabled(WasmtimeComponentInstance instance, WitResource self) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        try {
            boolean enabled = entry.socket != null ? entry.socket.getKeepAlive()
                    : Boolean.TRUE.equals(entry.pendingKeepAlive);
            return okResult(enabled);
        } catch (SocketException e) {
            return errorResult("unknown");
        }
    }

    @Override
    public WitResult tcpSocketSetKeepAliveEnabled(WasmtimeComponentInstance instance, WitResource self,
            boolean value) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.pendingKeepAlive = value;
        if (entry.socket != null) {
            try {
                entry.socket.setKeepAlive(value);
            } catch (SocketException e) {
                return errorResult("unknown");
            }
        }
        return okResult(null);
    }

    @Override
    public WitResult tcpSocketKeepAliveIdleTime(WasmtimeComponentInstance instance, WitResource self) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        return okResult(entry.keepAliveIdleTimeNanos);
    }

    @Override
    public WitResult tcpSocketSetKeepAliveIdleTime(WasmtimeComponentInstance instance, WitResource self,
            long value) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.keepAliveIdleTimeNanos = value;
        return okResult(null);
    }

    @Override
    public WitResult tcpSocketKeepAliveInterval(WasmtimeComponentInstance instance, WitResource self) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        return okResult(entry.keepAliveIntervalNanos);
    }

    @Override
    public WitResult tcpSocketSetKeepAliveInterval(WasmtimeComponentInstance instance, WitResource self,
            long value) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.keepAliveIntervalNanos = value;
        return okResult(null);
    }

    @Override
    public WitResult tcpSocketKeepAliveCount(WasmtimeComponentInstance instance, WitResource self) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        return okResult((int) entry.keepAliveCount);
    }

    @Override
    public WitResult tcpSocketSetKeepAliveCount(WasmtimeComponentInstance instance, WitResource self, int value) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.keepAliveCount = value;
        return okResult(null);
    }

    @Override
    public WitResult tcpSocketHopLimit(WasmtimeComponentInstance instance, WitResource self) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        return okResult((int) entry.hopLimit);
    }

    @Override
    public WitResult tcpSocketSetHopLimit(WasmtimeComponentInstance instance, WitResource self, int value) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.hopLimit = (short) value;
        return okResult(null);
    }

    @Override
    public WitResult tcpSocketReceiveBufferSize(WasmtimeComponentInstance instance, WitResource self) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        try {
            long size = entry.socket != null ? entry.socket.getReceiveBufferSize()
                    : entry.pendingReceiveBufferSize != null ? entry.pendingReceiveBufferSize : 65536L;
            return okResult(size);
        } catch (SocketException e) {
            return errorResult("unknown");
        }
    }

    @Override
    public WitResult tcpSocketSetReceiveBufferSize(WasmtimeComponentInstance instance, WitResource self,
            long value) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.pendingReceiveBufferSize = value;
        if (entry.socket != null) {
            try {
                entry.socket.setReceiveBufferSize((int) value);
            } catch (SocketException e) {
                return errorResult("unknown");
            }
        }
        return okResult(null);
    }

    @Override
    public WitResult tcpSocketSendBufferSize(WasmtimeComponentInstance instance, WitResource self) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        try {
            long size = entry.socket != null ? entry.socket.getSendBufferSize()
                    : entry.pendingSendBufferSize != null ? entry.pendingSendBufferSize : 65536L;
            return okResult(size);
        } catch (SocketException e) {
            return errorResult("unknown");
        }
    }

    @Override
    public WitResult tcpSocketSetSendBufferSize(WasmtimeComponentInstance instance, WitResource self, long value) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.pendingSendBufferSize = value;
        if (entry.socket != null) {
            try {
                entry.socket.setSendBufferSize((int) value);
            } catch (SocketException e) {
                return errorResult("unknown");
            }
        }
        return okResult(null);
    }

    @Override
    public WitResource tcpSocketSubscribe(WasmtimeComponentInstance instance, WitResource self) {
        int rep = io.registerPollableDeadline(WasiIoResources.ALWAYS_READY);
        return WitResource.own("pollable", rep);
    }

    @Override
    public WitResult tcpSocketShutdown(WasmtimeComponentInstance instance, WitResource self, WitEnum shutdownType) {
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null || entry.socket == null) {
            return errorResult("invalid-state");
        }
        try {
            String type = shutdownType.name();
            if ("receive".equals(type) || "both".equals(type)) {
                entry.socket.shutdownInput();
            }
            if ("send".equals(type) || "both".equals(type)) {
                entry.socket.shutdownOutput();
            }
            return okResult(null);
        } catch (IOException e) {
            return errorResult("unknown");
        }
    }

    // ---- wasi:sockets/udp ----------------------------------------------------

    @Override
    public WitResult udpSocketStartBind(WasmtimeComponentInstance instance, WitResource self, WitResource network,
            WitVariant localAddress) {
        UdpSocketEntry entry = udpSockets.get(self.rep());
        if (entry == null || entry.bound) {
            return errorResult("invalid-state");
        }
        entry.pendingLocalAddress = toInetSocketAddress(localAddress);
        return okResult(null);
    }

    @Override
    public WitResult udpSocketFinishBind(WasmtimeComponentInstance instance, WitResource self) {
        UdpSocketEntry entry = udpSockets.get(self.rep());
        if (entry == null || entry.pendingLocalAddress == null) {
            return errorResult("not-in-progress");
        }
        try (DatagramSocket socket = new DatagramSocket(entry.pendingLocalAddress);) {

            if (entry.pendingReceiveBufferSize != null) {
                socket.setReceiveBufferSize(entry.pendingReceiveBufferSize.intValue());
            }
            if (entry.pendingSendBufferSize != null) {
                socket.setSendBufferSize(entry.pendingSendBufferSize.intValue());
            }
            entry.socket = socket;
            entry.bound = true;
            return okResult(null);
        } catch (SocketException e) {
            return errorResult(mapException(e));
        }
    }

    @Override
    public WitResult udpSocketStream(WasmtimeComponentInstance instance, WitResource self,
            Optional<Object> remoteAddress) {
        UdpSocketEntry entry = udpSockets.get(self.rep());
        if (entry == null || !entry.bound) {
            return errorResult("invalid-state");
        }
        if (remoteAddress.isPresent()) {
            InetSocketAddress remote = toInetSocketAddress((WitVariant) remoteAddress.get());
            try {
                entry.socket.connect(remote);
            } catch (SocketException e) {
                return errorResult(mapException(e));
            }
            entry.connectedRemote = remote;
        } else if (entry.socket.isConnected()) {
            entry.socket.disconnect();
            entry.connectedRemote = null;
        }

        int inRep = nextRep.getAndIncrement();
        int outRep = nextRep.getAndIncrement();
        incomingDatagramStreams.put(inRep, entry);
        outgoingDatagramStreams.put(outRep, entry);
        return okResult(new Object[] {
                WitResource.own("incoming-datagram-stream", inRep),
                WitResource.own("outgoing-datagram-stream", outRep) });
    }

    @Override
    public WitResult udpSocketLocalAddress(WasmtimeComponentInstance instance, WitResource self) {
        UdpSocketEntry entry = udpSockets.get(self.rep());
        if (entry == null || entry.socket == null) {
            return errorResult("invalid-state");
        }
        return okResult(toWitIpSocketAddress((InetSocketAddress) entry.socket.getLocalSocketAddress()));
    }

    @Override
    public WitResult udpSocketRemoteAddress(WasmtimeComponentInstance instance, WitResource self) {
        UdpSocketEntry entry = udpSockets.get(self.rep());
        if (entry == null || entry.connectedRemote == null) {
            return errorResult("invalid-state");
        }
        return okResult(toWitIpSocketAddress(entry.connectedRemote));
    }

    /**
     * IPv4 only (see class javadoc), so this always reports a fixed {@code
     * ipv4}.
     */
    @Override
    public WitEnum udpSocketAddressFamily(WasmtimeComponentInstance instance, WitResource self) {
        return new WitEnum("ipv4");
    }

    @Override
    public WitResult udpSocketUnicastHopLimit(WasmtimeComponentInstance instance, WitResource self) {
        UdpSocketEntry entry = udpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        return okResult((int) entry.hopLimit);
    }

    @Override
    public WitResult udpSocketSetUnicastHopLimit(WasmtimeComponentInstance instance, WitResource self, int value) {
        UdpSocketEntry entry = udpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.hopLimit = (short) value;
        return okResult(null);
    }

    @Override
    public WitResult udpSocketReceiveBufferSize(WasmtimeComponentInstance instance, WitResource self) {
        UdpSocketEntry entry = udpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        try {
            long size = entry.socket != null ? entry.socket.getReceiveBufferSize()
                    : entry.pendingReceiveBufferSize != null ? entry.pendingReceiveBufferSize : 65536L;
            return okResult(size);
        } catch (SocketException e) {
            return errorResult("unknown");
        }
    }

    @Override
    public WitResult udpSocketSetReceiveBufferSize(WasmtimeComponentInstance instance, WitResource self,
            long value) {
        UdpSocketEntry entry = udpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.pendingReceiveBufferSize = value;
        if (entry.socket != null) {
            try {
                entry.socket.setReceiveBufferSize((int) value);
            } catch (SocketException e) {
                return errorResult("unknown");
            }
        }
        return okResult(null);
    }

    @Override
    public WitResult udpSocketSendBufferSize(WasmtimeComponentInstance instance, WitResource self) {
        UdpSocketEntry entry = udpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        try {
            long size = entry.socket != null ? entry.socket.getSendBufferSize()
                    : entry.pendingSendBufferSize != null ? entry.pendingSendBufferSize : 65536L;
            return okResult(size);
        } catch (SocketException e) {
            return errorResult("unknown");
        }
    }

    @Override
    public WitResult udpSocketSetSendBufferSize(WasmtimeComponentInstance instance, WitResource self, long value) {
        UdpSocketEntry entry = udpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.pendingSendBufferSize = value;
        if (entry.socket != null) {
            try {
                entry.socket.setSendBufferSize((int) value);
            } catch (SocketException e) {
                return errorResult("unknown");
            }
        }
        return okResult(null);
    }

    @Override
    public WitResource udpSocketSubscribe(WasmtimeComponentInstance instance, WitResource self) {
        int rep = io.registerPollableDeadline(WasiIoResources.ALWAYS_READY);
        return WitResource.own("pollable", rep);
    }

    @Override
    public WitResult incomingDatagramStreamReceive(WasmtimeComponentInstance instance, WitResource self,
            long maxResults) {
        UdpSocketEntry entry = incomingDatagramStreams.get(self.rep());
        if (entry == null || entry.socket == null) {
            return errorResult("invalid-state");
        }
        if (maxResults == 0) {
            return okResult(new ArrayList<>());
        }
        try {
            byte[] buffer = new byte[65536];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            entry.socket.receive(packet);
            Map<String, Object> datagram = new LinkedHashMap<>();
            byte[] data = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
            datagram.put("data", data);
            datagram.put("remote-address",
                    toWitIpSocketAddress(new InetSocketAddress(packet.getAddress(), packet.getPort())));
            List<Object> results = new ArrayList<>();
            results.add(datagram);
            return okResult(results);
        } catch (IOException e) {
            return errorResult(mapException(e));
        }
    }

    @Override
    public WitResource incomingDatagramStreamSubscribe(WasmtimeComponentInstance instance, WitResource self) {
        int rep = io.registerPollableDeadline(WasiIoResources.ALWAYS_READY);
        return WitResource.own("pollable", rep);
    }

    @Override
    public WitResult outgoingDatagramStreamCheckSend(WasmtimeComponentInstance instance, WitResource self) {
        UdpSocketEntry entry = outgoingDatagramStreams.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        return okResult(1024L);
    }

    @SuppressWarnings("unchecked")
    @Override
    public WitResult outgoingDatagramStreamSend(WasmtimeComponentInstance instance, WitResource self,
            List<Object> datagrams) {
        UdpSocketEntry entry = outgoingDatagramStreams.get(self.rep());
        if (entry == null || entry.socket == null) {
            return errorResult("invalid-state");
        }
        long sent = 0;
        for (Object item : datagrams) {
            Map<String, Object> datagram = (Map<String, Object>) item;
            byte[] data = (byte[]) datagram.get("data");
            Optional<WitVariant> remoteAddress = (Optional<WitVariant>) datagram.get("remote-address");
            InetSocketAddress target = remoteAddress.isPresent() ? toInetSocketAddress(remoteAddress.get())
                    : entry.connectedRemote;
            if (target == null) {
                return errorResult("invalid-argument");
            }
            try {
                entry.socket.send(new DatagramPacket(data, data.length, target));
                sent++;
            } catch (IOException e) {
                if (sent > 0) {
                    break;
                }
                return errorResult(mapException(e));
            }
        }
        return okResult(sent);
    }

    @Override
    public WitResource outgoingDatagramStreamSubscribe(WasmtimeComponentInstance instance, WitResource self) {
        int rep = io.registerPollableDeadline(WasiIoResources.ALWAYS_READY);
        return WitResource.own("pollable", rep);
    }

    // ---- wasi:sockets/ip-name-lookup -----------------------------------------

    @Override
    public WitResult ipNameLookupResolveAddresses(WasmtimeComponentInstance instance, WitResource network,
            String name) {
        try {
            List<InetAddress> resolved = Arrays.stream(InetAddress.getAllByName(name))
                    .filter(address -> !(address instanceof Inet6Address)).toList();
            int rep = nextRep.getAndIncrement();
            resolveStreams.put(rep, resolved.iterator());
            return okResult(WitResource.own("resolve-address-stream", rep));
        } catch (UnknownHostException e) {
            return errorResult("name-unresolvable");
        }
    }

    @Override
    public WitResult resolveAddressStreamResolveNextAddress(WasmtimeComponentInstance instance, WitResource self) {
        Iterator<InetAddress> iterator = resolveStreams.get(self.rep());
        if (iterator == null) {
            return errorResult("invalid-state");
        }
        if (!iterator.hasNext()) {
            return okResult(Optional.empty());
        }
        return okResult(Optional.of(toWitIpAddress(iterator.next())));
    }

    @Override
    public WitResource resolveAddressStreamSubscribe(WasmtimeComponentInstance instance, WitResource self) {
        int rep = io.registerPollableDeadline(WasiIoResources.ALWAYS_READY);
        return WitResource.own("pollable", rep);
    }

    @Override
    public WasiSocketsContext withVersion(SemanticVersion version) {
        if (!supportsVersion(version)) {
            throw new IllegalArgumentException("Unsupported version: " + version);
        }
        this.version = version;
        return this;
    }

    @Override
    public SemanticVersion getVersion() {
        return this.version;
    }

    @Override
    public SemanticVersion getMiniumVersion() {
        return new SemanticVersion(0, 0, 1);
    }

    @Override
    public SemanticVersion getMaximumVersion() {
        return new SemanticVersion(0, 3, 0);
    }
}
