package io.github.stefanrichterhuber.wasmtimejavang;

import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Represents a shared WebAssembly linear memory.
 * Shared memory can be shared across multiple instances and stores.
 * This class provides methods to read from and write to the WASM memory
 * from the host Java application.
 */
public final class WasmtimeSharedMemory implements AutoCloseable, WasmtimeMemory {
    private final WasmtimeEngine engine;
    private long sharedMemoryPtr;
    private ByteBuffer buffer;

    private native long createSharedMemory(long enginePtr, long initialPages, long maxPages);

    private native static void closeSharedMemory(long sharedMemoryPtr);

    private native ByteBuffer getDirectBuffer(long sharedMemoryPtr);

    private native long getMemorySize(long sharedMemoryPtr);

    private native void growMemory(long sharedMemoryPtr, long delta);

    private final Cleaner.Cleanable cleanable;

    private static class CleanState implements Runnable {
        private final long sharedMemoryPtr;

        CleanState(long sharedMemoryPtr) {
            this.sharedMemoryPtr = sharedMemoryPtr;
        }

        @Override
        public void run() {
            WasmtimeSharedMemory.closeSharedMemory(sharedMemoryPtr);
        }
    }

    /**
     * Creates a new WasmtimeSharedMemory.
     * 
     * @param engine       The engine to associate with this memory.
     * @param initialPages Initial number of pages (64KiB each).
     * @param maxPages     Maximum number of pages.
     */
    public WasmtimeSharedMemory(WasmtimeEngine engine, long initialPages, long maxPages) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.sharedMemoryPtr = createSharedMemory(engine.getEnginePtr(), initialPages, maxPages);
        this.cleanable = WasmtimeEngine.CLEANER.register(this, new CleanState(this.sharedMemoryPtr));

    }

    /**
     * Returns the native pointer to the shared memory.
     * 
     * @return The native shared memory pointer.
     */
    long getSharedMemoryPtr() {
        if (sharedMemoryPtr == 0) {
            throw new IllegalStateException("Shared memory no longer active");
        }
        return this.sharedMemoryPtr;
    }

    /**
     * Returns a ByteBuffer view of the WASM memory.
     * 
     * @return A ByteBuffer mapping the WASM memory.
     */
    @Override
    public ByteBuffer buffer() {
        final long currentSize = getMemorySize(getSharedMemoryPtr());
        if (buffer == null || buffer.capacity() != currentSize) {
            buffer = getDirectBuffer(getSharedMemoryPtr());
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        return buffer;
    }

    /**
     * Grows the memory by the specified number of pages.
     * 
     * @param delta The number of pages to add.
     */
    @Override
    public void grow(long delta) {
        growMemory(getSharedMemoryPtr(), delta);
        // Invalidate buffer to force refresh on next access
        this.buffer = null;
    }

    @Override
    public void close() throws Exception {
        if (sharedMemoryPtr != 0) {
            this.cleanable.clean();
        }
        sharedMemoryPtr = 0;
    }

    @Override
    public boolean isShared() {
        return true;
    }
}
