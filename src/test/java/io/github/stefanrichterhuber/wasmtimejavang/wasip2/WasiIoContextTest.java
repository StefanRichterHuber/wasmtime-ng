package io.github.stefanrichterhuber.wasmtimejavang.wasip2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext.ComponentImportFunction;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext.ComponentImportResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;

/**
 * Direct unit tests for {@link WasiIoContext}: every import function/resource
 * is exercised here by calling the (package-visible) implementation methods
 * directly, rather than only relying on what a real wasm component happens to
 * invoke at runtime (see {@code WasmtimeWasiP2Test} for the end-to-end
 * counterpart). This is what gets {@code [method]output-stream.check-write},
 * {@code [method]output-stream.subscribe}, error paths, and drop/destructor
 * behavior under test without needing a wasm program built specifically to
 * hit each one.
 */
public class WasiIoContextTest {

    private static WitResource resourceOf(int rep) {
        return new WitResource(null, rep, true);
    }

    private static final class ThrowingOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            throw new IOException("boom");
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            throw new IOException("boom");
        }

        @Override
        public void flush() throws IOException {
            throw new IOException("boom");
        }
    }

    private static final class CountingCloseOutputStream extends ByteArrayOutputStream {
        int closeCount = 0;

        @Override
        public void close() throws IOException {
            closeCount++;
            super.close();
        }
    }

    private static final class ThrowingInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("boom");
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            throw new IOException("boom");
        }
    }

    @Test
    public void nameAndProvidedInterfaces() {
        WasiIoContext io = new WasiIoContext();
        assertEquals("wasi-io", io.name());
        assertEquals(WasiIoContext.NAME, io.name());
        assertTrue(io.getProvidedInterfaces().contains("wasi:io/poll@0.2.6"));
        assertTrue(io.getProvidedInterfaces().contains("wasi:io/streams@0.2.6"));
        assertTrue(io.getProvidedInterfaces().contains("wasi:io/error@0.2.6"));
    }

    @Test
    public void importFunctionsCoverEveryDeclaredMethod() {
        WasiIoContext io = new WasiIoContext();
        List<ComponentImportFunction> functions = io.getImportFunctions();
        List<String> names = functions.stream().map(ComponentImportFunction::funcName).toList();
        assertTrue(names.contains("[method]pollable.block"));
        assertTrue(names.contains("[method]input-stream.blocking-read"));
        assertTrue(names.contains("[method]input-stream.subscribe"));
        assertTrue(names.contains("[method]output-stream.check-write"));
        assertTrue(names.contains("[method]output-stream.write"));
        assertTrue(names.contains("[method]output-stream.blocking-write-and-flush"));
        assertTrue(names.contains("[method]output-stream.blocking-flush"));
        assertTrue(names.contains("[method]output-stream.subscribe"));
        assertTrue(functions.stream().allMatch(f -> f.interfaceName().startsWith("wasi:io/")));
    }

    @Test
    public void importResourcesCoverEveryDeclaredResource() {
        WasiIoContext io = new WasiIoContext();
        List<ComponentImportResource> resources = io.getImportResources();
        assertTrue(resources.stream().anyMatch(r -> r.interfaceName().equals("wasi:io/poll@0.2.6")
                && r.resourceName().equals("pollable")));
        assertTrue(resources.stream().anyMatch(r -> r.interfaceName().equals("wasi:io/streams@0.2.6")
                && r.resourceName().equals("input-stream")));
        assertTrue(resources.stream().anyMatch(r -> r.interfaceName().equals("wasi:io/streams@0.2.6")
                && r.resourceName().equals("output-stream")));
        assertTrue(resources.stream().anyMatch(r -> r.interfaceName().equals("wasi:io/error@0.2.6")
                && r.resourceName().equals("error")));

        // The "error" resource destructor is a documented no-op (never actually
        // constructed by this implementation); just verify it doesn't throw.
        ComponentImportResource errorResource = resources.stream()
                .filter(r -> r.resourceName().equals("error")).findFirst().orElseThrow();
        assertDoesNotThrow(() -> errorResource.destructor().drop(123));
    }

    @Test
    public void outputStreamRegisterGetDrop() {
        WasiIoContext io = new WasiIoContext();
        CountingCloseOutputStream out = new CountingCloseOutputStream();
        int rep = io.registerOutputStream(out);

        assertEquals(out, io.getOutputStream(rep));
        io.dropOutputStream(rep);
        assertNull(io.getOutputStream(rep));
        assertEquals(1, out.closeCount, "dropOutputStream should close the underlying stream");

        // Dropping again (already removed) must not throw.
        assertDoesNotThrow(() -> io.dropOutputStream(rep));
    }

    @Test
    public void dropOutputStreamSwallowsIoExceptionOnClose() {
        WasiIoContext io = new WasiIoContext();
        OutputStream throwsOnClose = new OutputStream() {
            @Override
            public void write(int b) {
                // unused
            }

            @Override
            public void close() throws IOException {
                throw new IOException("close failed");
            }
        };
        int rep = io.registerOutputStream(throwsOnClose);
        assertDoesNotThrow(() -> io.dropOutputStream(rep));
    }

    @Test
    public void inputStreamRegisterGetDrop() {
        WasiIoContext io = new WasiIoContext();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
        int rep = io.registerInputStream(in);

        assertEquals(in, io.getInputStream(rep));
        io.dropInputStream(rep);
        assertNull(io.getInputStream(rep));
    }

    @Test
    public void pollableRegisterGetDrop() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerPollableDeadline(42L);

        assertEquals(42L, io.getPollableDeadline(rep));
        io.dropPollable(rep);
        assertNull(io.getPollableDeadline(rep));
    }

    @Test
    public void registeredReplaceAreUniqueReps() {
        WasiIoContext io = new WasiIoContext();
        int repA = io.registerOutputStream(new ByteArrayOutputStream());
        int repB = io.registerOutputStream(new ByteArrayOutputStream());
        int repC = io.registerInputStream(new ByteArrayInputStream(new byte[0]));
        assertTrue(repA != repB && repB != repC && repA != repC);
    }

    @Test
    public void pollableBlockReturnsImmediatelyWhenAlwaysReady() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerPollableDeadline(WasiIoResources.ALWAYS_READY);

        long start = System.nanoTime();
        Object[] result = io.pollableBlock(null, new Object[] { resourceOf(rep) });
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertEquals(0, result.length);
        assertTrue(elapsedMillis < 200, "expected an immediate return, took " + elapsedMillis + "ms");
    }

    @Test
    public void pollableBlockReturnsImmediatelyForPastDeadline() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerPollableDeadline(System.nanoTime() - 1_000_000_000L);

        long start = System.nanoTime();
        io.pollableBlock(null, new Object[] { resourceOf(rep) });
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(elapsedMillis < 200, "expected an immediate return for a past deadline, took " + elapsedMillis
                + "ms");
    }

    @Test
    public void pollableBlockSleepsUntilFutureDeadline() {
        WasiIoContext io = new WasiIoContext();
        long durationNanos = 100_000_000L; // 100ms
        int rep = io.registerPollableDeadline(System.nanoTime() + durationNanos);

        long start = System.nanoTime();
        io.pollableBlock(null, new Object[] { resourceOf(rep) });
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(elapsedMillis >= 80, "expected to block for roughly 100ms, took " + elapsedMillis + "ms");
    }

    @Test
    public void pollableBlockOnUnknownResourceDoesNotThrowOrBlock() {
        WasiIoContext io = new WasiIoContext();
        long start = System.nanoTime();
        assertDoesNotThrow(() -> io.pollableBlock(null, new Object[] { resourceOf(999) }));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(elapsedMillis < 200);
    }

    @Test
    public void outputStreamCheckWrite() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerOutputStream(new ByteArrayOutputStream());

        Object[] okResult = io.outputStreamCheckWrite(null, new Object[] { resourceOf(rep) });
        WitResult ok = (WitResult) okResult[0];
        assertTrue(ok.ok());
        assertEquals(65536L, ok.value());

        Object[] errResult = io.outputStreamCheckWrite(null, new Object[] { resourceOf(999) });
        WitResult err = (WitResult) errResult[0];
        assertFalse(err.ok());
    }

    @Test
    public void outputStreamWriteSuccessAndErrorPaths() {
        WasiIoContext io = new WasiIoContext();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rep = io.registerOutputStream(out);
        byte[] payload = "hello".getBytes();

        Object[] result = io.outputStreamWrite(null, new Object[] { resourceOf(rep), payload });
        assertTrue(((WitResult) result[0]).ok());
        assertArrayEquals(payload, out.toByteArray());

        // Unregistered resource.
        Object[] missing = io.outputStreamWrite(null, new Object[] { resourceOf(999), payload });
        assertFalse(((WitResult) missing[0]).ok());

        // IOException path.
        int throwingRep = io.registerOutputStream(new ThrowingOutputStream());
        Object[] failed = io.outputStreamWrite(null, new Object[] { resourceOf(throwingRep), payload });
        assertFalse(((WitResult) failed[0]).ok());
    }

    @Test
    public void outputStreamBlockingWriteAndFlushSuccessAndErrorPaths() {
        WasiIoContext io = new WasiIoContext();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rep = io.registerOutputStream(out);
        byte[] payload = "world".getBytes();

        Object[] result = io.outputStreamBlockingWriteAndFlush(null, new Object[] { resourceOf(rep), payload });
        assertTrue(((WitResult) result[0]).ok());
        assertArrayEquals(payload, out.toByteArray());

        Object[] missing = io.outputStreamBlockingWriteAndFlush(null, new Object[] { resourceOf(999), payload });
        assertFalse(((WitResult) missing[0]).ok());

        int throwingRep = io.registerOutputStream(new ThrowingOutputStream());
        Object[] failed = io.outputStreamBlockingWriteAndFlush(null,
                new Object[] { resourceOf(throwingRep), payload });
        assertFalse(((WitResult) failed[0]).ok());
    }

    @Test
    public void outputStreamBlockingFlush() {
        WasiIoContext io = new WasiIoContext();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rep = io.registerOutputStream(out);

        Object[] result = io.outputStreamBlockingFlush(null, new Object[] { resourceOf(rep) });
        assertTrue(((WitResult) result[0]).ok());

        int throwingRep = io.registerOutputStream(new ThrowingOutputStream());
        Object[] failed = io.outputStreamBlockingFlush(null, new Object[] { resourceOf(throwingRep) });
        assertFalse(((WitResult) failed[0]).ok());
    }

    @Test
    public void outputStreamSubscribeReturnsAlwaysReadyPollable() {
        WasiIoContext io = new WasiIoContext();
        Object[] result = io.outputStreamSubscribe(null, new Object[0]);
        WitResource pollable = (WitResource) result[0];

        assertEquals("pollable", pollable.resourceName());
        assertTrue(pollable.owned());
        assertNotNull(io.getPollableDeadline(pollable.rep()));
        assertEquals(WasiIoResources.ALWAYS_READY, io.getPollableDeadline(pollable.rep()));
    }

    @Test
    public void inputStreamBlockingReadReturnsAvailableBytes() {
        WasiIoContext io = new WasiIoContext();
        byte[] content = "hello".getBytes();
        int rep = io.registerInputStream(new ByteArrayInputStream(content));

        Object[] result = io.inputStreamBlockingRead(null, new Object[] { resourceOf(rep), 100L });
        WitResult wr = (WitResult) result[0];
        assertTrue(wr.ok());
        assertArrayEquals(content, (byte[]) wr.value());
    }

    @Test
    public void inputStreamBlockingReadRespectsRequestedLength() {
        WasiIoContext io = new WasiIoContext();
        byte[] content = "hello world".getBytes();
        int rep = io.registerInputStream(new ByteArrayInputStream(content));

        Object[] result = io.inputStreamBlockingRead(null, new Object[] { resourceOf(rep), 5L });
        WitResult wr = (WitResult) result[0];
        assertTrue(wr.ok());
        assertArrayEquals(new byte[] { 'h', 'e', 'l', 'l', 'o' }, (byte[]) wr.value());
    }

    @Test
    public void inputStreamBlockingReadReportsClosedAtEof() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerInputStream(new ByteArrayInputStream(new byte[0]));

        Object[] result = io.inputStreamBlockingRead(null, new Object[] { resourceOf(rep), 10L });
        WitResult wr = (WitResult) result[0];
        assertFalse(wr.ok());
        assertEquals(new WitVariant("closed", null), wr.value());
    }

    @Test
    public void inputStreamBlockingReadOnUnknownResourceReportsClosed() {
        WasiIoContext io = new WasiIoContext();
        Object[] result = io.inputStreamBlockingRead(null, new Object[] { resourceOf(999), 10L });
        WitResult wr = (WitResult) result[0];
        assertFalse(wr.ok());
        assertEquals(new WitVariant("closed", null), wr.value());
    }

    @Test
    public void inputStreamBlockingReadReportsClosedOnIoException() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerInputStream(new ThrowingInputStream());

        Object[] result = io.inputStreamBlockingRead(null, new Object[] { resourceOf(rep), 10L });
        WitResult wr = (WitResult) result[0];
        assertFalse(wr.ok());
        assertEquals(new WitVariant("closed", null), wr.value());
    }

    @Test
    public void inputStreamSubscribeReturnsAlwaysReadyPollable() {
        WasiIoContext io = new WasiIoContext();
        Object[] result = io.inputStreamSubscribe(null, new Object[0]);
        WitResource pollable = (WitResource) result[0];

        assertEquals("pollable", pollable.resourceName());
        assertTrue(pollable.owned());
        assertEquals(WasiIoResources.ALWAYS_READY, io.getPollableDeadline(pollable.rep()));
    }
}
