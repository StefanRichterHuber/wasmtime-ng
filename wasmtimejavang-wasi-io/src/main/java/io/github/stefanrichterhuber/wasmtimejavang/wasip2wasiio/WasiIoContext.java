package io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasiio.ErrorContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasiio.PollContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasiio.StreamsContext;

/**
 * Implementation of {@code wasi:io/poll}, {@code wasi:io/streams} and
 * {@code wasi:io/error} (WASI Preview 2, 0.2.6) -- the {@code "wasi-io"}
 * component context.
 * <br>
 * Implements all three generated interfaces at once (see
 * {@link WasiRandomContext} for why this works and how
 * {@code getImportFunctions()}/{@code getImportResources()}/
 * {@code getProvidedInterfaces()} get combined).
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
 * loop calls instead, paired with {@code subscribe}/{@code poll}). The
 * non-blocking variant is approximated via {@link InputStream#available()}
 * (accurate enough for a socket's {@link InputStream}, the main consumer)
 * rather than true non-blocking I/O, which an arbitrary Java
 * {@link InputStream} can't generally provide; since every
 * {@code subscribe()}-produced pollable here is always "ready" (see
 * {@link #inputStreamSubscribe}), a guest polling in a loop while genuinely
 * waiting on a slow peer ends up busy-retrying -- {@link #inputStreamRead}
 * sleeps briefly on each empty result specifically to bound that to a few
 * hundred retries/second rather than a hot spin; {@code skip} reuses the same
 * approximation. Reads also collapse every error (including a real
 * {@link IOException}, not just end-of-stream) to the WIT
 * {@code stream-error.closed} case rather than {@code last-operation-failed},
 * since that would require constructing an actual {@code error} resource --
 * for the same reason, {@link #errorToDebugString} has no real message to
 * report and returns a generic, rep-derived label.
 */
public class WasiIoContext implements PollContext, ErrorContext, StreamsContext, WasiIoResources {
    private static final Logger LOGGER = LogManager.getLogger();

    /** The stable name other contexts reference via {@code getDependencies()}. */
    public static final String NAME = "wasi-io";

    /**
     * Upper bound on how many bytes a single {@code blocking-read} call returns.
     */
    private static final int MAX_READ_CHUNK = 65536;

    private final Map<Integer, InputStream> inputStreams = new ConcurrentHashMap<>();
    private final Map<Integer, OutputStream> outputStreams = new ConcurrentHashMap<>();
    private final Map<Integer, Long> pollables = new ConcurrentHashMap<>();
    private final AtomicInteger nextRep = new AtomicInteger(1);
    private SemanticVersion version = DEFAULT_VERSION;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> getProvidedInterfaces() {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(PollContext.super.getProvidedInterfaces());
        result.addAll(ErrorContext.super.getProvidedInterfaces());
        result.addAll(StreamsContext.super.getProvidedInterfaces());
        return result;
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
    public void dropError(int rep) {
        // The "error" resource is never actually constructed by this
        // implementation (writes/flushes never fail with a real error
        // resource), so there is nothing to release; this only exists to
        // satisfy the component's declared import surface.
    }

    @Override
    public List<ComponentImportFunction> getImportFunctions() {
        List<ComponentImportFunction> result = new ArrayList<>();
        result.addAll(PollContext.super.getImportFunctions());
        result.addAll(ErrorContext.super.getImportFunctions());
        result.addAll(StreamsContext.super.getImportFunctions());
        return result;
    }

    @Override
    public List<ComponentImportResource> getImportResources() {
        List<ComponentImportResource> result = new ArrayList<>();
        result.addAll(PollContext.super.getImportResources());
        result.addAll(ErrorContext.super.getImportResources());
        result.addAll(StreamsContext.super.getImportResources());
        return result;
    }

    /**
     * Implementation of {@code [method]pollable.ready}: reports whether the
     * pollable's deadline (if any) has already passed, without blocking.
     */
    @Override
    public boolean pollableReady(WasmtimeComponentInstance instance, WitResource self) {
        Long deadline = pollables.get(self.rep());
        return deadline == null || deadline == ALWAYS_READY || deadline <= System.nanoTime();
    }

    /**
     * Implementation of {@code [method]pollable.block}: blocks the calling
     * thread until the pollable's deadline (if any) has passed.
     */
    @Override
    public void pollableBlock(WasmtimeComponentInstance instance, WitResource self) {
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
    @Override
    public List<Object> pollPoll(WasmtimeComponentInstance instance, List<Object> in) {
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
                return ready;
            }
            long remainingNanos = earliestDeadline - System.nanoTime();
            try {
                Thread.sleep(Math.max(1, Math.min(remainingNanos / 1_000_000L, 50)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ready;
            }
        }
    }

    @Override
    public String errorToDebugString(WasmtimeComponentInstance instance, WitResource self) {
        // No real "error" resource is ever constructed with actual failure content (see
        // class
        // javadoc), so there is no genuine message to report -- just a generic,
        // rep-derived label.
        return "error(rep=" + self.rep() + ")";
    }

    @Override
    public WitResult outputStreamCheckWrite(WasmtimeComponentInstance instance, WitResource self) {
        OutputStream out = outputStreams.get(self.rep());
        if (out == null) {
            return WitResult.err(null);
        }
        return WitResult.ok(65536L);
    }

    @Override
    public WitResult outputStreamWrite(WasmtimeComponentInstance instance, WitResource self, byte[] contents) {
        OutputStream out = outputStreams.get(self.rep());
        if (out == null) {
            return WitResult.err(null);
        }
        try {
            out.write(contents);
            return WitResult.ok(null);
        } catch (IOException e) {
            return WitResult.err(null);
        }
    }

    @Override
    public WitResult outputStreamBlockingWriteAndFlush(WasmtimeComponentInstance instance, WitResource self,
            byte[] contents) {
        OutputStream out = outputStreams.get(self.rep());
        if (out == null) {
            return WitResult.err(null);
        }
        try {
            out.write(contents);
            out.flush();
            return WitResult.ok(null);
        } catch (IOException e) {
            return WitResult.err(null);
        }
    }

    /**
     * Non-blocking variant of {@link #outputStreamBlockingFlush} -- identical
     * body, since every host call in this bridge is already synchronous from
     * the guest's perspective (same rationale {@code WasiSocketsContext}
     * documents for its own non-blocking/blocking pairs).
     */
    @Override
    public WitResult outputStreamFlush(WasmtimeComponentInstance instance, WitResource self) {
        return outputStreamBlockingFlush(instance, self);
    }

    @Override
    public WitResult outputStreamBlockingFlush(WasmtimeComponentInstance instance, WitResource self) {
        OutputStream out = outputStreams.get(self.rep());
        if (out != null) {
            try {
                out.flush();
            } catch (IOException e) {
                return WitResult.err(null);
            }
        }
        return WitResult.ok(null);
    }

    @Override
    public WitResource outputStreamSubscribe(WasmtimeComponentInstance instance, WitResource self) {
        int rep = registerPollableDeadline(ALWAYS_READY);
        return WitResource.own("pollable", rep);
    }

    @Override
    public WitResult outputStreamWriteZeroes(WasmtimeComponentInstance instance, WitResource self, long len) {
        OutputStream out = outputStreams.get(self.rep());
        if (out == null) {
            return WitResult.err(null);
        }
        try {
            out.write(new byte[(int) len]);
            return WitResult.ok(null);
        } catch (IOException e) {
            return WitResult.err(null);
        }
    }

    @Override
    public WitResult outputStreamBlockingWriteZeroesAndFlush(WasmtimeComponentInstance instance, WitResource self,
            long len) {
        OutputStream out = outputStreams.get(self.rep());
        if (out == null) {
            return WitResult.err(null);
        }
        try {
            out.write(new byte[(int) len]);
            out.flush();
            return WitResult.ok(null);
        } catch (IOException e) {
            return WitResult.err(null);
        }
    }

    /**
     * Non-blocking splice: reads whatever's immediately available from
     * {@code src} (the same {@link InputStream#available()} approximation
     * {@link #inputStreamRead} uses) and writes it straight to this stream.
     */
    @Override
    public WitResult outputStreamSplice(WasmtimeComponentInstance instance, WitResource self, WitResource src,
            long len) {
        OutputStream out = outputStreams.get(self.rep());
        InputStream in = inputStreams.get(src.rep());
        if (out == null || in == null) {
            return WitResult.err(null);
        }
        try {
            int available = in.available();
            if (available <= 0) {
                return WitResult.ok(0L);
            }
            int toRead = (int) Math.min(len, Math.min(available, MAX_READ_CHUNK));
            byte[] buffer = new byte[toRead];
            int n = in.read(buffer);
            if (n < 0) {
                return WitResult.err(null);
            }
            out.write(buffer, 0, n);
            return WitResult.ok((long) n);
        } catch (IOException e) {
            return WitResult.err(null);
        }
    }

    @Override
    public WitResult outputStreamBlockingSplice(WasmtimeComponentInstance instance, WitResource self, WitResource src,
            long len) {
        OutputStream out = outputStreams.get(self.rep());
        InputStream in = inputStreams.get(src.rep());
        if (out == null || in == null) {
            return WitResult.err(null);
        }
        try {
            int toRead = (int) Math.min(len, MAX_READ_CHUNK);
            byte[] buffer = new byte[toRead];
            int n = in.read(buffer);
            if (n < 0) {
                return WitResult.err(null);
            }
            out.write(buffer, 0, n);
            return WitResult.ok((long) n);
        } catch (IOException e) {
            return WitResult.err(null);
        }
    }

    /**
     * Implementation of {@code [method]input-stream.blocking-read}: performs a
     * plain blocking {@link InputStream#read(byte[])} for up to {@code len}
     * bytes (capped at {@value #MAX_READ_CHUNK}). See the class javadoc for
     * why this is the only read variant implemented, and why every failure
     * (including a real {@link IOException}) is reported as
     * {@code stream-error.closed}.
     */
    @Override
    public WitResult inputStreamBlockingRead(WasmtimeComponentInstance instance, WitResource self, long len) {
        InputStream in = inputStreams.get(self.rep());
        if (in == null) {
            return WitResult.err(new WitVariant("closed", null));
        }
        int toRead = (int) Math.min(len, MAX_READ_CHUNK);
        byte[] buffer = new byte[toRead];
        try {
            int n = in.read(buffer);
            if (n < 0) {
                return WitResult.err(new WitVariant("closed", null));
            }
            byte[] read = (n == buffer.length) ? buffer : Arrays.copyOf(buffer, n);
            return WitResult.ok(read);
        } catch (IOException e) {
            return WitResult.err(new WitVariant("closed", null));
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
    @Override
    public WitResult inputStreamRead(WasmtimeComponentInstance instance, WitResource self, long len) {
        InputStream in = inputStreams.get(self.rep());
        if (in == null) {
            return WitResult.err(new WitVariant("closed", null));
        }
        try {
            int available = in.available();
            if (available <= 0) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return WitResult.ok(new byte[0]);
            }
            int toRead = (int) Math.min(len, Math.min(available, MAX_READ_CHUNK));
            byte[] buffer = new byte[toRead];
            int n = in.read(buffer);
            if (n < 0) {
                return WitResult.err(new WitVariant("closed", null));
            }
            byte[] read = (n == buffer.length) ? buffer : Arrays.copyOf(buffer, n);
            return WitResult.ok(read);
        } catch (IOException e) {
            return WitResult.err(new WitVariant("closed", null));
        }
    }

    /**
     * Implementation of {@code [method]input-stream.blocking-skip}: skips up
     * to {@code len} bytes via a single {@link InputStream#skip} call,
     * mirroring how {@link #inputStreamBlockingRead} performs a single
     * blocking read rather than looping to fill the requested length.
     */
    @Override
    public WitResult inputStreamBlockingSkip(WasmtimeComponentInstance instance, WitResource self, long len) {
        InputStream in = inputStreams.get(self.rep());
        if (in == null) {
            return WitResult.err(new WitVariant("closed", null));
        }
        try {
            long skipped = in.skip(len);
            return WitResult.ok(skipped);
        } catch (IOException e) {
            return WitResult.err(new WitVariant("closed", null));
        }
    }

    /**
     * Non-blocking variant of {@link #inputStreamBlockingSkip}, using the
     * same {@link InputStream#available()} approximation as
     * {@link #inputStreamRead}.
     */
    @Override
    public WitResult inputStreamSkip(WasmtimeComponentInstance instance, WitResource self, long len) {
        InputStream in = inputStreams.get(self.rep());
        if (in == null) {
            return WitResult.err(new WitVariant("closed", null));
        }
        try {
            int available = in.available();
            if (available <= 0) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return WitResult.ok(0L);
            }
            long toSkip = Math.min(len, Math.min(available, MAX_READ_CHUNK));
            long skipped = in.skip(toSkip);
            return WitResult.ok(skipped);
        } catch (IOException e) {
            return WitResult.err(new WitVariant("closed", null));
        }
    }

    @Override
    public WitResource inputStreamSubscribe(WasmtimeComponentInstance instance, WitResource self) {
        int rep = registerPollableDeadline(ALWAYS_READY);
        return WitResource.own("pollable", rep);
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
