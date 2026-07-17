package io.github.stefanrichterhuber.wasmtimejavang.wasip2;

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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup;
import io.github.stefanrichterhuber.wasmtimejavang.ComponentFunction;
import io.github.stefanrichterhuber.wasmtimejavang.ResourceDestructor;
import io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitEnum;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;

/**
 * Implementation of {@code wasi:sockets/network}, {@code instance-network},
 * {@code tcp-create-socket}, {@code tcp}, {@code udp-create-socket},
 * {@code udp} and {@code ip-name-lookup} (WASI Preview 2, 0.2.6) -- the
 * {@code "wasi-sockets"} component context.
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
 * {@code not-supported} at socket creation. Several options WASI exposes
 * that {@code java.net} has no equivalent for (TCP {@code hop-limit} and
 * {@code keep-alive-idle-time}/{@code interval}/{@code count}, UDP
 * {@code unicast-hop-limit}) are stored and returned as configured but not
 * actually applied to the OS socket -- documented per-method below.
 * <br>
 * Depends on {@code "wasi-io"} ({@link WasiIoResources}): TCP connection
 * streams are registered in -- and pollables are always allocated from --
 * the same shared tables {@code wasi:io/streams}/{@code wasi:io/poll}
 * operate on, since {@code [method]pollable.block} and the free {@code poll}
 * function (both implemented by {@link WasiIoContext}) only ever look a
 * pollable up in that one shared table.
 */
public class WasiSocketsContext implements WasmComponentContext {

    /** The stable name other contexts reference via {@code getDependencies()}. */
    public static final String NAME = "wasi-sockets";

    private static final String WASI_SOCKETS_NETWORK = "wasi:sockets/network";
    private static final String WASI_SOCKETS_INSTANCE_NETWORK = "wasi:sockets/instance-network";
    private static final String WASI_SOCKETS_TCP_CREATE_SOCKET = "wasi:sockets/tcp-create-socket";
    private static final String WASI_SOCKETS_TCP = "wasi:sockets/tcp";
    private static final String WASI_SOCKETS_UDP_CREATE_SOCKET = "wasi:sockets/udp-create-socket";
    private static final String WASI_SOCKETS_UDP = "wasi:sockets/udp";
    private static final String WASI_SOCKETS_IP_NAME_LOOKUP = "wasi:sockets/ip-name-lookup";

    private static final Set<String> PROVIDED_INTERFACES = Set.of(
            WASI_SOCKETS_NETWORK, WASI_SOCKETS_INSTANCE_NETWORK, WASI_SOCKETS_TCP_CREATE_SOCKET, WASI_SOCKETS_TCP,
            WASI_SOCKETS_UDP_CREATE_SOCKET, WASI_SOCKETS_UDP, WASI_SOCKETS_IP_NAME_LOOKUP);

    /** How long {@code start-connect} blocks waiting for a TCP handshake. */
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    private SemanticVersion version = WasiCliContext.DEFAULT_VERSION;
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
        return PROVIDED_INTERFACES;
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
        String instanceNetwork = WASI_SOCKETS_INSTANCE_NETWORK + "@" + version;
        String tcpCreate = WASI_SOCKETS_TCP_CREATE_SOCKET + "@" + version;
        String tcp = WASI_SOCKETS_TCP + "@" + version;
        String udpCreate = WASI_SOCKETS_UDP_CREATE_SOCKET + "@" + version;
        String udp = WASI_SOCKETS_UDP + "@" + version;
        String ipNameLookup = WASI_SOCKETS_IP_NAME_LOOKUP + "@" + version;

        result.add(func(instanceNetwork, "instance-network", this::instanceNetwork));
        result.add(func(tcpCreate, "create-tcp-socket", this::createTcpSocket));

        result.add(func(tcp, "[method]tcp-socket.start-bind", this::tcpStartBind));
        result.add(func(tcp, "[method]tcp-socket.finish-bind", this::tcpFinishBind));
        result.add(func(tcp, "[method]tcp-socket.start-connect", this::tcpStartConnect));
        result.add(func(tcp, "[method]tcp-socket.finish-connect", this::tcpFinishConnect));
        result.add(func(tcp, "[method]tcp-socket.start-listen", this::tcpStartListen));
        result.add(func(tcp, "[method]tcp-socket.finish-listen", this::tcpFinishListen));
        result.add(func(tcp, "[method]tcp-socket.accept", this::tcpAccept));
        result.add(func(tcp, "[method]tcp-socket.local-address", this::tcpLocalAddress));
        result.add(func(tcp, "[method]tcp-socket.remote-address", this::tcpRemoteAddress));
        result.add(func(tcp, "[method]tcp-socket.is-listening", this::tcpIsListening));
        result.add(func(tcp, "[method]tcp-socket.set-listen-backlog-size", this::tcpSetListenBacklogSize));
        result.add(func(tcp, "[method]tcp-socket.keep-alive-enabled", this::tcpKeepAliveEnabled));
        result.add(func(tcp, "[method]tcp-socket.set-keep-alive-enabled", this::tcpSetKeepAliveEnabled));
        result.add(func(tcp, "[method]tcp-socket.keep-alive-idle-time", this::tcpKeepAliveIdleTime));
        result.add(func(tcp, "[method]tcp-socket.set-keep-alive-idle-time", this::tcpSetKeepAliveIdleTime));
        result.add(func(tcp, "[method]tcp-socket.keep-alive-interval", this::tcpKeepAliveInterval));
        result.add(func(tcp, "[method]tcp-socket.set-keep-alive-interval", this::tcpSetKeepAliveInterval));
        result.add(func(tcp, "[method]tcp-socket.keep-alive-count", this::tcpKeepAliveCount));
        result.add(func(tcp, "[method]tcp-socket.set-keep-alive-count", this::tcpSetKeepAliveCount));
        result.add(func(tcp, "[method]tcp-socket.hop-limit", this::tcpHopLimit));
        result.add(func(tcp, "[method]tcp-socket.set-hop-limit", this::tcpSetHopLimit));
        result.add(func(tcp, "[method]tcp-socket.receive-buffer-size", this::tcpReceiveBufferSize));
        result.add(func(tcp, "[method]tcp-socket.set-receive-buffer-size", this::tcpSetReceiveBufferSize));
        result.add(func(tcp, "[method]tcp-socket.send-buffer-size", this::tcpSendBufferSize));
        result.add(func(tcp, "[method]tcp-socket.set-send-buffer-size", this::tcpSetSendBufferSize));
        result.add(func(tcp, "[method]tcp-socket.subscribe", this::tcpSubscribe));
        result.add(func(tcp, "[method]tcp-socket.shutdown", this::tcpShutdown));

        result.add(func(udpCreate, "create-udp-socket", this::createUdpSocket));

        result.add(func(udp, "[method]udp-socket.start-bind", this::udpStartBind));
        result.add(func(udp, "[method]udp-socket.finish-bind", this::udpFinishBind));
        result.add(func(udp, "[method]udp-socket.stream", this::udpStream));
        result.add(func(udp, "[method]udp-socket.local-address", this::udpLocalAddress));
        result.add(func(udp, "[method]udp-socket.remote-address", this::udpRemoteAddress));
        result.add(func(udp, "[method]udp-socket.unicast-hop-limit", this::udpUnicastHopLimit));
        result.add(func(udp, "[method]udp-socket.set-unicast-hop-limit", this::udpSetUnicastHopLimit));
        result.add(func(udp, "[method]udp-socket.receive-buffer-size", this::udpReceiveBufferSize));
        result.add(func(udp, "[method]udp-socket.set-receive-buffer-size", this::udpSetReceiveBufferSize));
        result.add(func(udp, "[method]udp-socket.send-buffer-size", this::udpSendBufferSize));
        result.add(func(udp, "[method]udp-socket.set-send-buffer-size", this::udpSetSendBufferSize));
        result.add(func(udp, "[method]udp-socket.subscribe", this::udpSubscribe));
        result.add(func(udp, "[method]incoming-datagram-stream.receive", this::incomingDatagramReceive));
        result.add(func(udp, "[method]incoming-datagram-stream.subscribe", this::incomingDatagramSubscribe));
        result.add(func(udp, "[method]outgoing-datagram-stream.check-send", this::outgoingDatagramCheckSend));
        result.add(func(udp, "[method]outgoing-datagram-stream.send", this::outgoingDatagramSend));
        result.add(func(udp, "[method]outgoing-datagram-stream.subscribe", this::outgoingDatagramSubscribe));

        result.add(func(ipNameLookup, "resolve-addresses", this::resolveAddresses));
        result.add(func(ipNameLookup, "[method]resolve-address-stream.resolve-next-address",
                this::resolveNextAddress));
        result.add(func(ipNameLookup, "[method]resolve-address-stream.subscribe", this::resolveAddressSubscribe));

        return result;
    }

    @Override
    public List<ComponentImportResource> getImportResources() {
        List<ComponentImportResource> result = new ArrayList<>();
        String version = "@" + this.version;
        for (String iface : List.of(WASI_SOCKETS_NETWORK, WASI_SOCKETS_INSTANCE_NETWORK, WASI_SOCKETS_TCP,
                WASI_SOCKETS_UDP, WASI_SOCKETS_IP_NAME_LOOKUP)) {
            result.add(resource(iface + version, "network", this::dropNetwork));
        }
        result.add(resource(WASI_SOCKETS_TCP_CREATE_SOCKET + version, "tcp-socket", this::dropTcpSocket));
        result.add(resource(WASI_SOCKETS_TCP + version, "tcp-socket", this::dropTcpSocket));
        result.add(resource(WASI_SOCKETS_TCP + version, "input-stream", io::dropInputStream));
        result.add(resource(WASI_SOCKETS_TCP + version, "output-stream", io::dropOutputStream));
        result.add(resource(WASI_SOCKETS_TCP + version, "pollable", io::dropPollable));

        result.add(resource(WASI_SOCKETS_UDP_CREATE_SOCKET + version, "udp-socket", this::dropUdpSocket));
        result.add(resource(WASI_SOCKETS_UDP + version, "udp-socket", this::dropUdpSocket));
        result.add(resource(WASI_SOCKETS_UDP + version, "incoming-datagram-stream",
                this::dropIncomingDatagramStream));
        result.add(resource(WASI_SOCKETS_UDP + version, "outgoing-datagram-stream",
                this::dropOutgoingDatagramStream));
        result.add(resource(WASI_SOCKETS_UDP + version, "pollable", io::dropPollable));

        result.add(resource(WASI_SOCKETS_IP_NAME_LOOKUP + version, "resolve-address-stream",
                this::dropResolveAddressStream));
        result.add(resource(WASI_SOCKETS_IP_NAME_LOOKUP + version, "pollable", io::dropPollable));
        return result;
    }

    private static ComponentImportFunction func(String interfaceName, String funcName, ComponentFunction function) {
        return new ComponentImportFunction(interfaceName, funcName, function);
    }

    private static ComponentImportResource resource(String interfaceName, String resourceName,
            ResourceDestructor destructor) {
        return new ComponentImportResource(interfaceName, resourceName, destructor);
    }

    private void dropNetwork(int rep) {
        networks.remove(rep);
    }

    private void dropTcpSocket(int rep) {
        TcpSocketEntry entry = tcpSockets.remove(rep);
        if (entry != null) {
            closeQuietly(entry.serverSocket);
            closeQuietly(entry.socket);
        }
    }

    private void dropUdpSocket(int rep) {
        UdpSocketEntry entry = udpSockets.remove(rep);
        if (entry != null) {
            closeQuietly(entry.socket);
        }
    }

    private void dropIncomingDatagramStream(int rep) {
        incomingDatagramStreams.remove(rep);
    }

    private void dropOutgoingDatagramStream(int rep) {
        outgoingDatagramStreams.remove(rep);
    }

    private void dropResolveAddressStream(int rep) {
        resolveStreams.remove(rep);
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

    private static Object[] okResult(Object value) {
        return new Object[] { WitResult.ok(value) };
    }

    private static Object[] errorResult(String errorCode) {
        return new Object[] { WitResult.err(new WitEnum(errorCode)) };
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

    protected Object[] instanceNetwork(WasmtimeComponentInstance instance, Object[] args) {
        int rep = nextRep.getAndIncrement();
        networks.put(rep, Boolean.TRUE);
        return new Object[] { WitResource.own("network", rep) };
    }

    protected Object[] createTcpSocket(WasmtimeComponentInstance instance, Object[] args) {
        WitEnum addressFamily = (WitEnum) args[0];
        if (!"ipv4".equals(addressFamily.name())) {
            return errorResult("not-supported");
        }
        int rep = nextRep.getAndIncrement();
        tcpSockets.put(rep, new TcpSocketEntry());
        return okResult(WitResource.own("tcp-socket", rep));
    }

    protected Object[] createUdpSocket(WasmtimeComponentInstance instance, Object[] args) {
        WitEnum addressFamily = (WitEnum) args[0];
        if (!"ipv4".equals(addressFamily.name())) {
            return errorResult("not-supported");
        }
        int rep = nextRep.getAndIncrement();
        udpSockets.put(rep, new UdpSocketEntry());
        return okResult(WitResource.own("udp-socket", rep));
    }

    // ---- wasi:sockets/tcp ---------------------------------------------------

    protected Object[] tcpStartBind(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        WitVariant localAddress = (WitVariant) args[2];
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

    protected Object[] tcpFinishBind(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null || !entry.bound) {
            return errorResult("not-in-progress");
        }
        return okResult(null);
    }

    protected Object[] tcpStartConnect(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        WitVariant remoteAddress = (WitVariant) args[2];
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

    protected Object[] tcpFinishConnect(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
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

    protected Object[] tcpStartListen(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null || !entry.bound) {
            return errorResult("invalid-state");
        }
        try {
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.bind(entry.localAddress, (int) entry.backlog);
            entry.serverSocket = serverSocket;
            entry.pendingError = null;
        } catch (IOException e) {
            entry.pendingError = mapException(e);
        }
        return okResult(null);
    }

    protected Object[] tcpFinishListen(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
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

    protected Object[] tcpAccept(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
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

    protected Object[] tcpLocalAddress(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
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

    protected Object[] tcpRemoteAddress(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null || entry.socket == null) {
            return errorResult("invalid-state");
        }
        return okResult(toWitIpSocketAddress((InetSocketAddress) entry.socket.getRemoteSocketAddress()));
    }

    protected Object[] tcpIsListening(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        return new Object[] { entry != null && entry.listening };
    }

    protected Object[] tcpSetListenBacklogSize(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        long value = (Long) args[1];
        TcpSocketEntry entry = tcpSockets.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.backlog = value;
        return okResult(null);
    }

    protected Object[] tcpKeepAliveEnabled(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
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

    protected Object[] tcpSetKeepAliveEnabled(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        boolean value = (Boolean) args[1];
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

    protected Object[] tcpKeepAliveIdleTime(WasmtimeComponentInstance instance, Object[] args) {
        TcpSocketEntry entry = tcpSockets.get(((WitResource) args[0]).rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        return okResult(entry.keepAliveIdleTimeNanos);
    }

    protected Object[] tcpSetKeepAliveIdleTime(WasmtimeComponentInstance instance, Object[] args) {
        TcpSocketEntry entry = tcpSockets.get(((WitResource) args[0]).rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.keepAliveIdleTimeNanos = (Long) args[1];
        return okResult(null);
    }

    protected Object[] tcpKeepAliveInterval(WasmtimeComponentInstance instance, Object[] args) {
        TcpSocketEntry entry = tcpSockets.get(((WitResource) args[0]).rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        return okResult(entry.keepAliveIntervalNanos);
    }

    protected Object[] tcpSetKeepAliveInterval(WasmtimeComponentInstance instance, Object[] args) {
        TcpSocketEntry entry = tcpSockets.get(((WitResource) args[0]).rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.keepAliveIntervalNanos = (Long) args[1];
        return okResult(null);
    }

    protected Object[] tcpKeepAliveCount(WasmtimeComponentInstance instance, Object[] args) {
        TcpSocketEntry entry = tcpSockets.get(((WitResource) args[0]).rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        return okResult((int) entry.keepAliveCount);
    }

    protected Object[] tcpSetKeepAliveCount(WasmtimeComponentInstance instance, Object[] args) {
        TcpSocketEntry entry = tcpSockets.get(((WitResource) args[0]).rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.keepAliveCount = (Integer) args[1];
        return okResult(null);
    }

    protected Object[] tcpHopLimit(WasmtimeComponentInstance instance, Object[] args) {
        TcpSocketEntry entry = tcpSockets.get(((WitResource) args[0]).rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        return okResult((int) entry.hopLimit);
    }

    protected Object[] tcpSetHopLimit(WasmtimeComponentInstance instance, Object[] args) {
        TcpSocketEntry entry = tcpSockets.get(((WitResource) args[0]).rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.hopLimit = (short) (int) (Integer) args[1];
        return okResult(null);
    }

    protected Object[] tcpReceiveBufferSize(WasmtimeComponentInstance instance, Object[] args) {
        TcpSocketEntry entry = tcpSockets.get(((WitResource) args[0]).rep());
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

    protected Object[] tcpSetReceiveBufferSize(WasmtimeComponentInstance instance, Object[] args) {
        TcpSocketEntry entry = tcpSockets.get(((WitResource) args[0]).rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        long value = (Long) args[1];
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

    protected Object[] tcpSendBufferSize(WasmtimeComponentInstance instance, Object[] args) {
        TcpSocketEntry entry = tcpSockets.get(((WitResource) args[0]).rep());
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

    protected Object[] tcpSetSendBufferSize(WasmtimeComponentInstance instance, Object[] args) {
        TcpSocketEntry entry = tcpSockets.get(((WitResource) args[0]).rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        long value = (Long) args[1];
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

    protected Object[] tcpSubscribe(WasmtimeComponentInstance instance, Object[] args) {
        int rep = io.registerPollableDeadline(WasiIoResources.ALWAYS_READY);
        return new Object[] { WitResource.own("pollable", rep) };
    }

    protected Object[] tcpShutdown(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        WitEnum shutdownType = (WitEnum) args[1];
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

    protected Object[] udpStartBind(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        WitVariant localAddress = (WitVariant) args[2];
        UdpSocketEntry entry = udpSockets.get(self.rep());
        if (entry == null || entry.bound) {
            return errorResult("invalid-state");
        }
        entry.pendingLocalAddress = toInetSocketAddress(localAddress);
        return okResult(null);
    }

    protected Object[] udpFinishBind(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        UdpSocketEntry entry = udpSockets.get(self.rep());
        if (entry == null || entry.pendingLocalAddress == null) {
            return errorResult("not-in-progress");
        }
        try {
            DatagramSocket socket = new DatagramSocket(entry.pendingLocalAddress);
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

    protected Object[] udpStream(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        @SuppressWarnings("unchecked")
        Optional<WitVariant> remoteAddress = (Optional<WitVariant>) args[1];
        UdpSocketEntry entry = udpSockets.get(self.rep());
        if (entry == null || !entry.bound) {
            return errorResult("invalid-state");
        }
        if (remoteAddress.isPresent()) {
            InetSocketAddress remote = toInetSocketAddress(remoteAddress.get());
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

    protected Object[] udpLocalAddress(WasmtimeComponentInstance instance, Object[] args) {
        UdpSocketEntry entry = udpSockets.get(((WitResource) args[0]).rep());
        if (entry == null || entry.socket == null) {
            return errorResult("invalid-state");
        }
        return okResult(toWitIpSocketAddress((InetSocketAddress) entry.socket.getLocalSocketAddress()));
    }

    protected Object[] udpRemoteAddress(WasmtimeComponentInstance instance, Object[] args) {
        UdpSocketEntry entry = udpSockets.get(((WitResource) args[0]).rep());
        if (entry == null || entry.connectedRemote == null) {
            return errorResult("invalid-state");
        }
        return okResult(toWitIpSocketAddress(entry.connectedRemote));
    }

    protected Object[] udpUnicastHopLimit(WasmtimeComponentInstance instance, Object[] args) {
        UdpSocketEntry entry = udpSockets.get(((WitResource) args[0]).rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        return okResult((int) entry.hopLimit);
    }

    protected Object[] udpSetUnicastHopLimit(WasmtimeComponentInstance instance, Object[] args) {
        UdpSocketEntry entry = udpSockets.get(((WitResource) args[0]).rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        entry.hopLimit = (short) (int) (Integer) args[1];
        return okResult(null);
    }

    protected Object[] udpReceiveBufferSize(WasmtimeComponentInstance instance, Object[] args) {
        UdpSocketEntry entry = udpSockets.get(((WitResource) args[0]).rep());
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

    protected Object[] udpSetReceiveBufferSize(WasmtimeComponentInstance instance, Object[] args) {
        UdpSocketEntry entry = udpSockets.get(((WitResource) args[0]).rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        long value = (Long) args[1];
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

    protected Object[] udpSendBufferSize(WasmtimeComponentInstance instance, Object[] args) {
        UdpSocketEntry entry = udpSockets.get(((WitResource) args[0]).rep());
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

    protected Object[] udpSetSendBufferSize(WasmtimeComponentInstance instance, Object[] args) {
        UdpSocketEntry entry = udpSockets.get(((WitResource) args[0]).rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        long value = (Long) args[1];
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

    protected Object[] udpSubscribe(WasmtimeComponentInstance instance, Object[] args) {
        int rep = io.registerPollableDeadline(WasiIoResources.ALWAYS_READY);
        return new Object[] { WitResource.own("pollable", rep) };
    }

    protected Object[] incomingDatagramReceive(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        long maxResults = (Long) args[1];
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

    protected Object[] incomingDatagramSubscribe(WasmtimeComponentInstance instance, Object[] args) {
        int rep = io.registerPollableDeadline(WasiIoResources.ALWAYS_READY);
        return new Object[] { WitResource.own("pollable", rep) };
    }

    protected Object[] outgoingDatagramCheckSend(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        UdpSocketEntry entry = outgoingDatagramStreams.get(self.rep());
        if (entry == null) {
            return errorResult("invalid-state");
        }
        return okResult(1024L);
    }

    @SuppressWarnings("unchecked")
    protected Object[] outgoingDatagramSend(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        List<Object> datagrams = (List<Object>) args[1];
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

    protected Object[] outgoingDatagramSubscribe(WasmtimeComponentInstance instance, Object[] args) {
        int rep = io.registerPollableDeadline(WasiIoResources.ALWAYS_READY);
        return new Object[] { WitResource.own("pollable", rep) };
    }

    // ---- wasi:sockets/ip-name-lookup -----------------------------------------

    protected Object[] resolveAddresses(WasmtimeComponentInstance instance, Object[] args) {
        String name = (String) args[1];
        try {
            InetAddress[] resolved = InetAddress.getAllByName(name);
            int rep = nextRep.getAndIncrement();
            resolveStreams.put(rep, List.of(resolved).iterator());
            return okResult(WitResource.own("resolve-address-stream", rep));
        } catch (UnknownHostException e) {
            return errorResult("name-unresolvable");
        }
    }

    protected Object[] resolveNextAddress(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        Iterator<InetAddress> iterator = resolveStreams.get(self.rep());
        if (iterator == null) {
            return errorResult("invalid-state");
        }
        if (!iterator.hasNext()) {
            return okResult(Optional.empty());
        }
        return okResult(Optional.of(toWitIpAddress(iterator.next())));
    }

    protected Object[] resolveAddressSubscribe(WasmtimeComponentInstance instance, Object[] args) {
        int rep = io.registerPollableDeadline(WasiIoResources.ALWAYS_READY);
        return new Object[] { WitResource.own("pollable", rep) };
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
