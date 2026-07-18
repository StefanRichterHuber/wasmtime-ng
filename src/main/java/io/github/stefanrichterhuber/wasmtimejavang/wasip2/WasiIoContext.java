package io.github.stefanrichterhuber.wasmtimejavang.wasip2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.stefanrichterhuber.wasmtimejavang.ComponentFunction;
import io.github.stefanrichterhuber.wasmtimejavang.ResourceDestructor;
import io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;

/**
 * Implementation of {@code wasi:io/poll}, {@code wasi:io/streams} and
 * {@code wasi:io/error} (WASI Preview 2, 0.2.6) -- the {@code "wasi-io"}
 * component context.
 * <br>
 * Owns the actual stream/pollable tables, exposed via {@link WasiIoResources}
 * so contexts implementing other interfaces that hand out these same
 * resource kinds (e.g. {@code wasi:cli/stdout},
 * {@code wasi:clocks/monotonic-clock})
 * can depend on {@code "wasi-io"} and share them rather than keeping separate
 * tables.
 * <br>
 * {@code input-stream} reading implements both
 * {@code [method]input-stream.blocking-read} (what Rust's {@code std::io::Read}
 * calls for e.g. {@code wasi:cli/stdin}) and the non-blocking
 * {@code [method]input-stream.read} (what {@code std::net::TcpStream}'s read
 * loop calls instead, paired with {@code subscribe}/{@code poll}) -- but not
 * {@code skip}/{@code blocking-skip}, which nothing in this library's test
 * fixtures needs. The non-blocking variant is approximated via
 * {@link InputStream#available()} (accurate enough for a socket's
 * {@link InputStream}, the main consumer) rather than true non-blocking I/O,
 * which an arbitrary Java {@link InputStream} can't generally provide; since
 * every {@code subscribe()}-produced pollable here is always "ready" (see
 * {@link #inputStreamSubscribe}), a guest polling in a loop while genuinely
 * waiting on a slow peer ends up busy-retrying -- {@link #inputStreamRead}
 * sleeps briefly on each empty result specifically to bound that to a few
 * hundred retries/second rather than a hot spin. Reads also collapse every
 * error (including a real {@link IOException}, not just end-of-stream) to the
 * WIT {@code stream-error.closed} case rather than {@code last-operation-failed},
 * since that would require constructing an actual {@code error} resource.
 */
public class WasiIoContext implements WasmComponentContext, WasiIoResources {
    private static final Logger LOGGER = LogManager.getLogger();

    /** The stable name other contexts reference via {@code getDependencies()}. */
    public static final String NAME = "wasi-io";

    private static final String WASI_IO_POLL = "wasi:io/poll";
    private static final String WASI_IO_ERROR = "wasi:io/error";
    private static final String WASI_IO_STREAMS = "wasi:io/streams";

    /**
     * Upper bound on how many bytes a single {@code blocking-read} call returns.
     */
    private static final int MAX_READ_CHUNK = 65536;

    private final Map<Integer, InputStream> inputStreams = new ConcurrentHashMap<>();
    private final Map<Integer, OutputStream> outputStreams = new ConcurrentHashMap<>();
    private final Map<Integer, Long> pollables = new ConcurrentHashMap<>();
    private final AtomicInteger nextRep = new AtomicInteger(1);
    private SemanticVersion version = WasiCliContext.DEFAULT_VERSION;
    private static final Set<String> PROVIDED_INTERFACES = Set.of(WASI_IO_POLL, WASI_IO_ERROR, WASI_IO_STREAMS);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> getProvidedInterfaces() {
        return PROVIDED_INTERFACES;
    }

    @Override
    public int registerOutputStream(OutputStream out) {
        int rep = nextRep.getAndIncrement();
        outputStreams.put(rep, out);
        return rep;
    }

    @Override
    public OutputStream getOutputStream(int rep) {
        return outputStreams.get(rep);
    }

    @Override
    public void dropOutputStream(int rep) {
        OutputStream out = outputStreams.remove(rep);
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                LOGGER.debug("Failed to close output stream resource {}", rep, e);
            }
        }
    }

    @Override
    public int registerInputStream(InputStream in) {
        int rep = nextRep.getAndIncrement();
        inputStreams.put(rep, in);
        return rep;
    }

    @Override
    public InputStream getInputStream(int rep) {
        return inputStreams.get(rep);
    }

    @Override
    public void dropInputStream(int rep) {
        inputStreams.remove(rep);
    }

    @Override
    public int registerPollableDeadline(long deadlineNanos) {
        int rep = nextRep.getAndIncrement();
        pollables.put(rep, deadlineNanos);
        return rep;
    }

    @Override
    public Long getPollableDeadline(int rep) {
        return pollables.get(rep);
    }

    @Override
    public void dropPollable(int rep) {
        pollables.remove(rep);
    }

    @Override
    public List<ComponentImportFunction> getImportFunctions() {
        List<ComponentImportFunction> result = new ArrayList<>();
        result.add(func(WASI_IO_POLL + "@" + version, "[method]pollable.block", this::pollableBlock));
        result.add(func(WASI_IO_POLL + "@" + version, "poll", this::poll));
        result.add(func(WASI_IO_STREAMS + "@" + version, "[method]input-stream.blocking-read",
                this::inputStreamBlockingRead));
        result.add(func(WASI_IO_STREAMS + "@" + version, "[method]input-stream.read", this::inputStreamRead));
        result.add(func(WASI_IO_STREAMS + "@" + version, "[method]input-stream.subscribe", this::inputStreamSubscribe));
        result.add(func(WASI_IO_STREAMS + "@" + version, "[method]output-stream.check-write",
                this::outputStreamCheckWrite));
        result.add(func(WASI_IO_STREAMS + "@" + version, "[method]output-stream.write", this::outputStreamWrite));
        result.add(func(WASI_IO_STREAMS + "@" + version, "[method]output-stream.blocking-write-and-flush",
                this::outputStreamBlockingWriteAndFlush));
        result.add(func(WASI_IO_STREAMS + "@" + version, "[method]output-stream.blocking-flush",
                this::outputStreamBlockingFlush));
        result.add(
                func(WASI_IO_STREAMS + "@" + version, "[method]output-stream.subscribe", this::outputStreamSubscribe));
        return result;
    }

    @Override
    public List<ComponentImportResource> getImportResources() {
        return List.of(
                resource(WASI_IO_POLL + "@" + version, "pollable", this::dropPollable),
                resource(WASI_IO_STREAMS + "@" + version, "input-stream", this::dropInputStream),
                resource(WASI_IO_STREAMS + "@" + version, "output-stream", this::dropOutputStream),
                resource(WASI_IO_STREAMS + "@" + version, "error", this::dropNoop),
                resource(WASI_IO_STREAMS + "@" + version, "pollable", this::dropPollable),
                resource(WASI_IO_ERROR + "@" + version, "error", this::dropNoop));
    }

    private static ComponentImportFunction func(String interfaceName, String funcName, ComponentFunction function) {
        return new ComponentImportFunction(interfaceName, funcName, function);
    }

    private static ComponentImportResource resource(String interfaceName, String resourceName,
            ResourceDestructor destructor) {
        return new ComponentImportResource(interfaceName, resourceName, destructor);
    }

    private void dropNoop(int rep) {
        // The "error" resource is never actually constructed by this
        // implementation (writes/flushes never fail), so there is nothing to
        // release; this only exists to satisfy the component's declared
        // import surface.
    }

    /**
     * Implementation of {@code [method]pollable.block}: blocks the calling
     * thread until the pollable's deadline (if any) has passed.
     */
    protected Object[] pollableBlock(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        Long deadline = pollables.get(self.rep());
        if (deadline != null && deadline != ALWAYS_READY) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos > 0) {
                try {
                    Thread.sleep(remainingNanos / 1_000_000L, (int) (remainingNanos % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return new Object[0];
    }

    /**
     * Implementation of the free {@code poll} function: waits until at least
     * one of the given pollables is ready, then returns the indices (into
     * {@code in}) of every pollable that's ready. Since every pollable this
     * context hands out is either {@link #ALWAYS_READY} or backed by a real
     * {@code monotonic-clock} deadline, this only ever actually waits when
     * every given pollable has a future deadline -- rechecking periodically
     * so a newly-elapsed deadline is noticed promptly.
     */
    protected Object[] poll(WasmtimeComponentInstance instance, Object[] args) {
        @SuppressWarnings("unchecked")
        List<Object> in = (List<Object>) args[0];
        while (true) {
            List<Object> ready = new ArrayList<>();
            long earliestDeadline = Long.MAX_VALUE;
            for (int i = 0; i < in.size(); i++) {
                WitResource pollable = (WitResource) in.get(i);
                Long deadline = pollables.get(pollable.rep());
                if (deadline == null || deadline == ALWAYS_READY || deadline <= System.nanoTime()) {
                    ready.add(i);
                } else {
                    earliestDeadline = Math.min(earliestDeadline, deadline);
                }
            }
            if (!ready.isEmpty()) {
                return new Object[] { ready };
            }
            long remainingNanos = earliestDeadline - System.nanoTime();
            try {
                Thread.sleep(Math.max(1, Math.min(remainingNanos / 1_000_000L, 50)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Object[] { ready };
            }
        }
    }

    protected Object[] outputStreamCheckWrite(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        OutputStream out = outputStreams.get(self.rep());
        if (out == null) {
            return new Object[] { WitResult.err(null) };
        }
        return new Object[] { WitResult.ok(65536L) };
    }

    protected Object[] outputStreamWrite(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        byte[] contents = (byte[]) args[1];
        OutputStream out = outputStreams.get(self.rep());
        if (out == null) {
            return new Object[] { WitResult.err(null) };
        }
        try {
            out.write(contents);
            return new Object[] { WitResult.ok(null) };
        } catch (IOException e) {
            return new Object[] { WitResult.err(null) };
        }
    }

    protected Object[] outputStreamBlockingWriteAndFlush(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        byte[] contents = (byte[]) args[1];
        OutputStream out = outputStreams.get(self.rep());
        if (out == null) {
            return new Object[] { WitResult.err(null) };
        }
        try {
            out.write(contents);
            out.flush();
            return new Object[] { WitResult.ok(null) };
        } catch (IOException e) {
            return new Object[] { WitResult.err(null) };
        }
    }

    protected Object[] outputStreamBlockingFlush(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        OutputStream out = outputStreams.get(self.rep());
        if (out != null) {
            try {
                out.flush();
            } catch (IOException e) {
                return new Object[] { WitResult.err(null) };
            }
        }
        return new Object[] { WitResult.ok(null) };
    }

    protected Object[] outputStreamSubscribe(WasmtimeComponentInstance instance, Object[] args) {
        int rep = registerPollableDeadline(ALWAYS_READY);
        return new Object[] { WitResource.own("pollable", rep) };
    }

    /**
     * Implementation of {@code [method]input-stream.blocking-read}: performs a
     * plain blocking {@link InputStream#read(byte[])} for up to {@code len}
     * bytes (capped at {@value #MAX_READ_CHUNK}). See the class javadoc for
     * why this is the only read variant implemented, and why every failure
     * (including a real {@link IOException}) is reported as
     * {@code stream-error.closed}.
     */
    protected Object[] inputStreamBlockingRead(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        long len = (Long) args[1];
        InputStream in = inputStreams.get(self.rep());
        if (in == null) {
            return new Object[] { WitResult.err(new WitVariant("closed", null)) };
        }
        int toRead = (int) Math.min(len, MAX_READ_CHUNK);
        byte[] buffer = new byte[toRead];
        try {
            int n = in.read(buffer);
            if (n < 0) {
                return new Object[] { WitResult.err(new WitVariant("closed", null)) };
            }
            byte[] read = (n == buffer.length) ? buffer : Arrays.copyOf(buffer, n);
            return new Object[] { WitResult.ok(read) };
        } catch (IOException e) {
            return new Object[] { WitResult.err(new WitVariant("closed", null)) };
        }
    }

    /**
     * Implementation of the non-blocking {@code [method]input-stream.read}:
     * returns whatever's immediately available (possibly an empty list, if
     * nothing has arrived yet but the stream isn't closed) rather than
     * blocking for at least one byte. See the class javadoc for the
     * {@link InputStream#available()}-based approximation and the busy-retry
     * mitigation below.
     */
    protected Object[] inputStreamRead(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        long len = (Long) args[1];
        InputStream in = inputStreams.get(self.rep());
        if (in == null) {
            return new Object[] { WitResult.err(new WitVariant("closed", null)) };
        }
        try {
            int available = in.available();
            if (available <= 0) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new Object[] { WitResult.ok(new byte[0]) };
            }
            int toRead = (int) Math.min(len, Math.min(available, MAX_READ_CHUNK));
            byte[] buffer = new byte[toRead];
            int n = in.read(buffer);
            if (n < 0) {
                return new Object[] { WitResult.err(new WitVariant("closed", null)) };
            }
            byte[] read = (n == buffer.length) ? buffer : Arrays.copyOf(buffer, n);
            return new Object[] { WitResult.ok(read) };
        } catch (IOException e) {
            return new Object[] { WitResult.err(new WitVariant("closed", null)) };
        }
    }

    protected Object[] inputStreamSubscribe(WasmtimeComponentInstance instance, Object[] args) {
        int rep = registerPollableDeadline(ALWAYS_READY);
        return new Object[] { WitResource.own("pollable", rep) };
    }

    @Override
    public WasiIoContext withVersion(SemanticVersion version) {
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
