package io.github.stefanrichterhuber.wasmtimejavang;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Represents a WebAssembly linear memory.
 * This class provides methods to read from and write to the WASM memory
 * from the host Java application.
 * All multi-byte numeric operations use LITTLE_ENDIAN byte order as per the
 * WebAssembly specification.
 */
public class WasmtimeMemory extends AbstractWasmtimeMemory {

    private final WasmtimeInstance instance;
    private final WasmtimeStore store;
    private final String name;

    private ByteBuffer buffer;

    private native void growMemory(long instancePtr, long storePtr, String name, long delta);

    private native ByteBuffer getDirectBuffer(long instancePtr, long storePtr, String name);

    private native long getMemorySize(long instancePtr, long storePtr, String name);

    /**
     * Internal constructor for WasmtimeMemory.
     * 
     * @param instance The WASM instance this memory belongs to.
     * @param store    The store associated with the instance.
     * @param name     The name of the exported memory.
     */
    public WasmtimeMemory(WasmtimeInstance instance, WasmtimeStore store, String name) {
        this.instance = instance;
        this.store = store;
        this.name = name;
    }

    /**
     * Returns a ByteBuffer view of the WASM memory.
     * The buffer is cached and refreshed if the memory size changes.
     * 
     * @return A ByteBuffer mapping the WASM memory.
     */
    public ByteBuffer buffer() {
        final long currentSize = getMemorySize(getInstance().getInstancePtr(), getStore().getStorePtr(), getName());
        if (buffer == null || buffer.capacity() != currentSize) {
            buffer = getDirectBuffer(getInstance().getInstancePtr(), getStore().getStorePtr(), getName());
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        return buffer;
    }

    /**
     * Grows the memory by the specified number of pages.
     * Note that, by default, a WebAssembly memory's page size is 64KiB (aka 65536
     * or 216).
     * 
     * @param delta The number of pages to add.
     */
    public void grow(long delta) {
        growMemory(getInstance().getInstancePtr(), getStore().getStorePtr(), getName(), delta);
        // Invalidate buffer to force refresh on next access
        this.buffer = null;
    }

    /**
     * 
     * Returns the WasmtimeInstance this memory belongs to.
     * 
     * @return The WasmtimeInstance associated with this memory.
     */
    public WasmtimeInstance getInstance() {
        return instance;
    }

    /**
     * Returns the WasmtimeStore associated with this memory.
     * 
     * @return The WasmtimeStore of this memory.
     */
    public WasmtimeStore getStore() {
        return store;
    }

    /**
     * Returns the name of the memory
     * 
     * @return
     */
    public String getName() {
        return this.name;
    }

}
