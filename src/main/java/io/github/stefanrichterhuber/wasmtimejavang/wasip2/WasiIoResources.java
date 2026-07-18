package io.github.stefanrichterhuber.wasmtimejavang.wasip2;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Shared resource-table contract of the {@code "wasi-io"} context
 * ({@code wasi:io/poll}, {@code wasi:io/streams}, {@code wasi:io/error}),
 * used by any context that depends on {@code "wasi-io"} (e.g. one
 * implementing {@code wasi:cli/stdout} or {@code wasi:clocks/monotonic-clock})
 * to register/release the streams and pollables it hands out as resources.
 * <br>
 * Declared separately from {@link WasiIoContext} so a dependent only couples
 * to this contract, not to that specific implementation -- any alternative
 * {@code "wasi-io"} provider a user swaps in just needs to also implement
 * this interface.
 */
public interface WasiIoResources {

    /** Sentinel pollable deadline meaning "always ready" (e.g. for streams). */
    long ALWAYS_READY = Long.MIN_VALUE;

    /**
     * Registers a new owned {@code output-stream} resource.
     *
     * @param out The Java stream backing the resource.
     * @return The resource's opaque representation (table key).
     */
    int registerOutputStream(OutputStream out);

    /**
     * Looks up a previously registered {@code output-stream} resource.
     *
     * @param rep The resource's opaque representation.
     * @return The backing stream, or {@code null} if not (or no longer)
     *         registered.
     */
    OutputStream getOutputStream(int rep);

    /**
     * Releases an {@code output-stream} resource, invoked when the guest
     * drops it.
     *
     * @param rep The resource's opaque representation.
     */
    void dropOutputStream(int rep);

    /**
     * Registers a new owned {@code input-stream} resource.
     *
     * @param in The Java stream backing the resource.
     * @return The resource's opaque representation (table key).
     */
    int registerInputStream(InputStream in);

    /**
     * Looks up a previously registered {@code input-stream} resource.
     *
     * @param rep The resource's opaque representation.
     * @return The backing stream, or {@code null} if not (or no longer)
     *         registered.
     */
    InputStream getInputStream(int rep);

    /**
     * Releases an {@code input-stream} resource, invoked when the guest
     * drops it.
     *
     * @param rep The resource's opaque representation.
     */
    void dropInputStream(int rep);

    /**
     * Registers a new owned {@code pollable} resource with the given
     * absolute deadline.
     *
     * @param deadlineNanos The deadline in {@link System#nanoTime()} space,
     *                      or {@link #ALWAYS_READY}.
     * @return The resource's opaque representation (table key).
     */
    int registerPollableDeadline(long deadlineNanos);

    /**
     * Looks up a previously registered {@code pollable} resource's deadline.
     *
     * @param rep The resource's opaque representation.
     * @return The deadline, or {@code null} if not (or no longer)
     *         registered.
     */
    Long getPollableDeadline(int rep);

    /**
     * Releases a {@code pollable} resource, invoked when the guest drops it.
     *
     * @param rep The resource's opaque representation.
     */
    void dropPollable(int rep);
}
