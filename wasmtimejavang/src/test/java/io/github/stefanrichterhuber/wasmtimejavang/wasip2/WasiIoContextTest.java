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
        assertTrue(io.getProvidedInterfaces().contains("wasi:io/poll"));
        assertTrue(io.getProvidedInterfaces().contains("wasi:io/streams"));
        assertTrue(io.getProvidedInterfaces().contains("wasi:io/error"));
    }

    @Test
    public void importFunctionsCoverEveryDeclaredMethod() {
        WasiIoContext io = new WasiIoContext();
        List<ComponentImportFunction> functions = io.getImportFunctions();
        List<String> names = functions.stream().map(ComponentImportFunction::funcName).toList();
        assertTrue(names.contains("[method]pollable.ready"));
        assertTrue(names.contains("[method]pollable.block"));
        assertTrue(names.contains("poll"));
        assertTrue(names.contains("[method]error.to-debug-string"));
        assertTrue(names.contains("[method]input-stream.blocking-read"));
        assertTrue(names.contains("[method]input-stream.read"));
        assertTrue(names.contains("[method]input-stream.skip"));
        assertTrue(names.contains("[method]input-stream.blocking-skip"));
        assertTrue(names.contains("[method]input-stream.subscribe"));
        assertTrue(names.contains("[method]output-stream.check-write"));
        assertTrue(names.contains("[method]output-stream.write"));
        assertTrue(names.contains("[method]output-stream.blocking-write-and-flush"));
        assertTrue(names.contains("[method]output-stream.flush"));
        assertTrue(names.contains("[method]output-stream.blocking-flush"));
        assertTrue(names.contains("[method]output-stream.subscribe"));
        assertTrue(names.contains("[method]output-stream.write-zeroes"));
        assertTrue(names.contains("[method]output-stream.blocking-write-zeroes-and-flush"));
        assertTrue(names.contains("[method]output-stream.splice"));
        assertTrue(names.contains("[method]output-stream.blocking-splice"));
        assertTrue(functions.stream().allMatch(f -> f.interfaceName().startsWith("wasi:io/")));
    }

    @Test
    public void importResourcesCoverEveryDeclaredResource() {
        WasiIoContext io = new WasiIoContext();
        List<ComponentImportResource> resources = io.getImportResources();
        String poll = "wasi:io/poll@" + io.getVersion();
        String streams = "wasi:io/streams@" + io.getVersion();
        String error = "wasi:io/error@" + io.getVersion();
        assertTrue(resources.stream().anyMatch(r -> r.interfaceName().equals(poll)
                && r.resourceName().equals("pollable")));
        assertTrue(resources.stream().anyMatch(r -> r.interfaceName().equals(streams)
                && r.resourceName().equals("input-stream")));
        assertTrue(resources.stream().anyMatch(r -> r.interfaceName().equals(streams)
                && r.resourceName().equals("output-stream")));
        assertTrue(resources.stream().anyMatch(r -> r.interfaceName().equals(error)
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
    public void pollableReadyReflectsDeadline() {
        WasiIoContext io = new WasiIoContext();
        int readyRep = io.registerPollableDeadline(WasiIoResources.ALWAYS_READY);
        int pastRep = io.registerPollableDeadline(System.nanoTime() - 1_000_000_000L);
        int futureRep = io.registerPollableDeadline(System.nanoTime() + 1_000_000_000L);

        assertTrue(io.pollableReady(null, resourceOf(readyRep)));
        assertTrue(io.pollableReady(null, resourceOf(pastRep)));
        assertFalse(io.pollableReady(null, resourceOf(futureRep)));
        assertTrue(io.pollableReady(null, resourceOf(999)), "unknown pollable is treated as ready");
    }

    @Test
    public void pollableBlockReturnsImmediatelyWhenAlwaysReady() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerPollableDeadline(WasiIoResources.ALWAYS_READY);

        long start = System.nanoTime();
        io.pollableBlock(null, resourceOf(rep));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(elapsedMillis < 200, "expected an immediate return, took " + elapsedMillis + "ms");
    }

    @Test
    public void pollableBlockReturnsImmediatelyForPastDeadline() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerPollableDeadline(System.nanoTime() - 1_000_000_000L);

        long start = System.nanoTime();
        io.pollableBlock(null, resourceOf(rep));
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
        io.pollableBlock(null, resourceOf(rep));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(elapsedMillis >= 80, "expected to block for roughly 100ms, took " + elapsedMillis + "ms");
    }

    @Test
    public void pollableBlockOnUnknownResourceDoesNotThrowOrBlock() {
        WasiIoContext io = new WasiIoContext();
        long start = System.nanoTime();
        assertDoesNotThrow(() -> io.pollableBlock(null, resourceOf(999)));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(elapsedMillis < 200);
    }

    @Test
    public void errorToDebugStringReturnsRepDerivedLabel() {
        WasiIoContext io = new WasiIoContext();
        String debugString = io.errorToDebugString(null, resourceOf(42));
        assertTrue(debugString.contains("42"));
    }

    @Test
    public void outputStreamCheckWrite() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerOutputStream(new ByteArrayOutputStream());

        WitResult ok = io.outputStreamCheckWrite(null, resourceOf(rep));
        assertTrue(ok.ok());
        assertEquals(65536L, ok.value());

        WitResult err = io.outputStreamCheckWrite(null, resourceOf(999));
        assertFalse(err.ok());
    }

    @Test
    public void outputStreamWriteSuccessAndErrorPaths() {
        WasiIoContext io = new WasiIoContext();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rep = io.registerOutputStream(out);
        byte[] payload = "hello".getBytes();

        assertTrue(io.outputStreamWrite(null, resourceOf(rep), payload).ok());
        assertArrayEquals(payload, out.toByteArray());

        // Unregistered resource.
        assertFalse(io.outputStreamWrite(null, resourceOf(999), payload).ok());

        // IOException path.
        int throwingRep = io.registerOutputStream(new ThrowingOutputStream());
        assertFalse(io.outputStreamWrite(null, resourceOf(throwingRep), payload).ok());
    }

    @Test
    public void outputStreamBlockingWriteAndFlushSuccessAndErrorPaths() {
        WasiIoContext io = new WasiIoContext();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rep = io.registerOutputStream(out);
        byte[] payload = "world".getBytes();

        assertTrue(io.outputStreamBlockingWriteAndFlush(null, resourceOf(rep), payload).ok());
        assertArrayEquals(payload, out.toByteArray());

        assertFalse(io.outputStreamBlockingWriteAndFlush(null, resourceOf(999), payload).ok());

        int throwingRep = io.registerOutputStream(new ThrowingOutputStream());
        assertFalse(io.outputStreamBlockingWriteAndFlush(null, resourceOf(throwingRep), payload).ok());
    }

    @Test
    public void outputStreamFlushIsSameAsBlockingFlush() {
        WasiIoContext io = new WasiIoContext();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rep = io.registerOutputStream(out);

        assertTrue(io.outputStreamFlush(null, resourceOf(rep)).ok());

        int throwingRep = io.registerOutputStream(new ThrowingOutputStream());
        assertFalse(io.outputStreamFlush(null, resourceOf(throwingRep)).ok());
    }

    @Test
    public void outputStreamBlockingFlush() {
        WasiIoContext io = new WasiIoContext();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rep = io.registerOutputStream(out);

        assertTrue(io.outputStreamBlockingFlush(null, resourceOf(rep)).ok());

        int throwingRep = io.registerOutputStream(new ThrowingOutputStream());
        assertFalse(io.outputStreamBlockingFlush(null, resourceOf(throwingRep)).ok());
    }

    @Test
    public void outputStreamWriteZeroesWritesZeroBytes() {
        WasiIoContext io = new WasiIoContext();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rep = io.registerOutputStream(out);

        assertTrue(io.outputStreamWriteZeroes(null, resourceOf(rep), 4L).ok());
        assertArrayEquals(new byte[4], out.toByteArray());

        assertFalse(io.outputStreamWriteZeroes(null, resourceOf(999), 4L).ok());
    }

    @Test
    public void outputStreamBlockingWriteZeroesAndFlushWritesAndFlushes() {
        WasiIoContext io = new WasiIoContext();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rep = io.registerOutputStream(out);

        assertTrue(io.outputStreamBlockingWriteZeroesAndFlush(null, resourceOf(rep), 3L).ok());
        assertArrayEquals(new byte[3], out.toByteArray());

        int throwingRep = io.registerOutputStream(new ThrowingOutputStream());
        assertFalse(io.outputStreamBlockingWriteZeroesAndFlush(null, resourceOf(throwingRep), 3L).ok());
    }

    @Test
    public void outputStreamSpliceCopiesAvailableBytes() {
        WasiIoContext io = new WasiIoContext();
        int srcRep = io.registerInputStream(new ByteArrayInputStream("hello".getBytes()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int dstRep = io.registerOutputStream(out);

        WitResult result = io.outputStreamSplice(null, resourceOf(dstRep), resourceOf(srcRep), 100L);
        assertTrue(result.ok());
        assertEquals(5L, result.value());
        assertArrayEquals("hello".getBytes(), out.toByteArray());
    }

    @Test
    public void outputStreamSpliceOnUnknownResourceFails() {
        WasiIoContext io = new WasiIoContext();
        int srcRep = io.registerInputStream(new ByteArrayInputStream("hello".getBytes()));
        assertFalse(io.outputStreamSplice(null, resourceOf(999), resourceOf(srcRep), 100L).ok());
    }

    @Test
    public void outputStreamBlockingSpliceCopiesBytes() {
        WasiIoContext io = new WasiIoContext();
        int srcRep = io.registerInputStream(new ByteArrayInputStream("world".getBytes()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int dstRep = io.registerOutputStream(out);

        WitResult result = io.outputStreamBlockingSplice(null, resourceOf(dstRep), resourceOf(srcRep), 100L);
        assertTrue(result.ok());
        assertEquals(5L, result.value());
        assertArrayEquals("world".getBytes(), out.toByteArray());
    }

    @Test
    public void outputStreamSubscribeReturnsAlwaysReadyPollable() {
        WasiIoContext io = new WasiIoContext();
        WitResource pollable = io.outputStreamSubscribe(null, resourceOf(1));

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

        WitResult wr = io.inputStreamBlockingRead(null, resourceOf(rep), 100L);
        assertTrue(wr.ok());
        assertArrayEquals(content, (byte[]) wr.value());
    }

    @Test
    public void inputStreamBlockingReadRespectsRequestedLength() {
        WasiIoContext io = new WasiIoContext();
        byte[] content = "hello world".getBytes();
        int rep = io.registerInputStream(new ByteArrayInputStream(content));

        WitResult wr = io.inputStreamBlockingRead(null, resourceOf(rep), 5L);
        assertTrue(wr.ok());
        assertArrayEquals(new byte[] { 'h', 'e', 'l', 'l', 'o' }, (byte[]) wr.value());
    }

    @Test
    public void inputStreamBlockingReadReportsClosedAtEof() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerInputStream(new ByteArrayInputStream(new byte[0]));

        WitResult wr = io.inputStreamBlockingRead(null, resourceOf(rep), 10L);
        assertFalse(wr.ok());
        assertEquals(new WitVariant("closed", null), wr.value());
    }

    @Test
    public void inputStreamBlockingReadOnUnknownResourceReportsClosed() {
        WasiIoContext io = new WasiIoContext();
        WitResult wr = io.inputStreamBlockingRead(null, resourceOf(999), 10L);
        assertFalse(wr.ok());
        assertEquals(new WitVariant("closed", null), wr.value());
    }

    @Test
    public void inputStreamBlockingReadReportsClosedOnIoException() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerInputStream(new ThrowingInputStream());

        WitResult wr = io.inputStreamBlockingRead(null, resourceOf(rep), 10L);
        assertFalse(wr.ok());
        assertEquals(new WitVariant("closed", null), wr.value());
    }

    @Test
    public void inputStreamBlockingSkipSkipsBytes() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerInputStream(new ByteArrayInputStream("hello world".getBytes()));

        WitResult wr = io.inputStreamBlockingSkip(null, resourceOf(rep), 6L);
        assertTrue(wr.ok());
        assertEquals(6L, wr.value());

        WitResult remainder = io.inputStreamBlockingRead(null, resourceOf(rep), 100L);
        assertArrayEquals("world".getBytes(), (byte[]) remainder.value());
    }

    @Test
    public void inputStreamBlockingSkipOnUnknownResourceReportsClosed() {
        WasiIoContext io = new WasiIoContext();
        WitResult wr = io.inputStreamBlockingSkip(null, resourceOf(999), 10L);
        assertFalse(wr.ok());
    }

    private static final class NeverAvailableInputStream extends InputStream {
        @Override
        public int available() {
            return 0;
        }

        @Override
        public int read() throws IOException {
            throw new IOException("read() should not be called while available() == 0");
        }
    }

    @Test
    public void inputStreamReadReturnsAvailableBytesWithoutBlocking() throws Exception {
        WasiIoContext io = new WasiIoContext();
        byte[] content = "hello".getBytes();
        int rep = io.registerInputStream(new ByteArrayInputStream(content));

        WitResult wr = io.inputStreamRead(null, resourceOf(rep), 100L);
        assertTrue(wr.ok());
        assertArrayEquals(content, (byte[]) wr.value());
    }

    @Test
    public void inputStreamReadRespectsRequestedLength() throws Exception {
        WasiIoContext io = new WasiIoContext();
        byte[] content = "hello world".getBytes();
        int rep = io.registerInputStream(new ByteArrayInputStream(content));

        WitResult wr = io.inputStreamRead(null, resourceOf(rep), 5L);
        assertTrue(wr.ok());
        assertArrayEquals(new byte[] { 'h', 'e', 'l', 'l', 'o' }, (byte[]) wr.value());
    }

    @Test
    public void inputStreamReadReturnsEmptyWithoutBlockingWhenNothingAvailable() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerInputStream(new NeverAvailableInputStream());

        long start = System.nanoTime();
        WitResult wr = io.inputStreamRead(null, resourceOf(rep), 10L);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(wr.ok());
        assertEquals(0, ((byte[]) wr.value()).length);
        assertTrue(elapsedMillis < 200, "expected the busy-retry mitigation sleep to be brief, took " + elapsedMillis
                + "ms");
    }

    @Test
    public void inputStreamReadReportsClosedAtEof() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerInputStream(new ByteArrayInputStream(new byte[0]) {
            @Override
            public synchronized int available() {
                return 1; // force past the "nothing available" branch so read() runs and hits EOF
            }
        });

        WitResult wr = io.inputStreamRead(null, resourceOf(rep), 10L);
        assertFalse(wr.ok());
        assertEquals(new WitVariant("closed", null), wr.value());
    }

    @Test
    public void inputStreamReadOnUnknownResourceReportsClosed() {
        WasiIoContext io = new WasiIoContext();
        WitResult wr = io.inputStreamRead(null, resourceOf(999), 10L);
        assertFalse(wr.ok());
        assertEquals(new WitVariant("closed", null), wr.value());
    }

    private static final class AvailableButThrowingInputStream extends InputStream {
        @Override
        public int available() {
            return 1;
        }

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
    public void inputStreamReadReportsClosedOnIoException() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerInputStream(new AvailableButThrowingInputStream());

        WitResult wr = io.inputStreamRead(null, resourceOf(rep), 10L);
        assertFalse(wr.ok());
        assertEquals(new WitVariant("closed", null), wr.value());
    }

    @Test
    public void inputStreamSkipReturnsAvailableSkippedCountWithoutBlocking() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerInputStream(new ByteArrayInputStream("hello world".getBytes()));

        WitResult wr = io.inputStreamSkip(null, resourceOf(rep), 5L);
        assertTrue(wr.ok());
        assertEquals(5L, wr.value());

        WitResult remainder = io.inputStreamRead(null, resourceOf(rep), 100L);
        assertArrayEquals(" world".getBytes(), (byte[]) remainder.value());
    }

    @Test
    public void inputStreamSkipReturnsZeroWithoutBlockingWhenNothingAvailable() {
        WasiIoContext io = new WasiIoContext();
        int rep = io.registerInputStream(new NeverAvailableInputStream());

        long start = System.nanoTime();
        WitResult wr = io.inputStreamSkip(null, resourceOf(rep), 10L);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(wr.ok());
        assertEquals(0L, wr.value());
        assertTrue(elapsedMillis < 200);
    }

    @Test
    public void inputStreamSkipOnUnknownResourceReportsClosed() {
        WasiIoContext io = new WasiIoContext();
        WitResult wr = io.inputStreamSkip(null, resourceOf(999), 10L);
        assertFalse(wr.ok());
    }

    @Test
    public void pollReturnsIndexOfAlreadyReadyPollableImmediately() {
        WasiIoContext io = new WasiIoContext();
        WitResource ready = io.outputStreamSubscribe(null, resourceOf(1));

        long start = System.nanoTime();
        List<Object> readyIndices = io.pollPoll(null, List.of(ready));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertEquals(List.of(0), readyIndices);
        assertTrue(elapsedMillis < 200);
    }

    @Test
    public void pollOnlyReturnsIndicesOfReadyPollables() {
        WasiIoContext io = new WasiIoContext();
        int futureRep = io.registerPollableDeadline(System.nanoTime() + 300_000_000L);
        WitResource future = resourceOf(futureRep);
        WitResource ready = io.outputStreamSubscribe(null, resourceOf(1));

        List<Object> readyIndices = io.pollPoll(null, List.of(future, ready));
        assertEquals(List.of(1), readyIndices);
    }

    @Test
    public void pollBlocksUntilFutureDeadlineElapses() {
        WasiIoContext io = new WasiIoContext();
        long durationNanos = 100_000_000L;
        int rep = io.registerPollableDeadline(System.nanoTime() + durationNanos);

        long start = System.nanoTime();
        List<Object> readyIndices = io.pollPoll(null, List.of(resourceOf(rep)));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertEquals(List.of(0), readyIndices);
        assertTrue(elapsedMillis >= 80, "expected to block for roughly 100ms, took " + elapsedMillis + "ms");
    }

    @Test
    public void pollTreatsUnknownPollableAsReady() {
        WasiIoContext io = new WasiIoContext();
        List<Object> readyIndices = io.pollPoll(null, List.of(resourceOf(999)));
        assertEquals(List.of(0), readyIndices);
    }

    @Test
    public void inputStreamSubscribeReturnsAlwaysReadyPollable() {
        WasiIoContext io = new WasiIoContext();
        WitResource pollable = io.inputStreamSubscribe(null, resourceOf(1));

        assertEquals("pollable", pollable.resourceName());
        assertTrue(pollable.owned());
        assertEquals(WasiIoResources.ALWAYS_READY, io.getPollableDeadline(pollable.rep()));
    }
}
