package io.github.stefanrichterhuber.wasmtimejavang;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

/**
 * Represents a WebAssembly linear memory.
 * This class provides methods to read from and write to the WASM memory
 * from the host Java application.
 * All multi-byte numeric operations use LITTLE_ENDIAN byte order as per the
 * WebAssembly specification.
 */
public class WasmtimeMemory {
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
        this.name = name;
        this.store = store;
    }

    /**
     * Returns a ByteBuffer view of the WASM memory.
     * The buffer is cached and refreshed if the memory size changes.
     * 
     * @return A ByteBuffer mapping the WASM memory.
     */
    public ByteBuffer buffer() {
        final long currentSize = getMemorySize(instance.getInstancePtr(), store.getStorePtr(), this.name);
        if (buffer == null || buffer.capacity() != currentSize) {
            buffer = getDirectBuffer(instance.getInstancePtr(), store.getStorePtr(), this.name);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        return buffer;
    }

    /**
     * Writes a byte array to the specified memory address.
     * 
     * @param address The starting address in WASM memory.
     * @param data    The byte array to write.
     */
    public void write(int address, byte[] data) {
        buffer().put(address, data);
    }

    /**
     * Writes a single byte to the specified memory address.
     * 
     * @param address The address in WASM memory.
     * @param data    The byte to write.
     */
    public void write(int address, byte data) {
        buffer().put(address, data);
    }

    /**
     * Writes a 16-bit short to the specified memory address in little-endian order.
     * 
     * @param address The starting address in WASM memory.
     * @param value   The short value to write.
     */
    public void writeShort(int address, short value) {
        buffer().putShort(address, value);
    }

    /**
     * Writes a 32-bit integer to the specified memory address in little-endian
     * order.
     * 
     * @param address The starting address in WASM memory.
     * @param value   The integer value to write.
     */
    public void writeInt(int address, int value) {
        buffer().putInt(address, value);
    }

    /**
     * Writes a 64-bit long to the specified memory address in little-endian order.
     * 
     * @param address The starting address in WASM memory.
     * @param value   The long value to write.
     */
    public void writeLong(int address, long value) {
        buffer().putLong(address, value);
    }

    /**
     * Writes a string to the specified memory address using the given charset.
     * 
     * @param address The starting address in WASM memory.
     * @param str     The string to write.
     * @param charset The charset to use for encoding.
     */
    public void writeString(int address, String str, Charset charset) {
        final byte[] buf = str.getBytes(charset);
        this.write(address, buf);
    }

    /**
     * Writes a null-terminated string (C-style string) to the specified memory
     * address.
     * 
     * @param address The starting address in WASM memory.
     * @param str     The string to write.
     * @param charset The charset to use for encoding.
     */
    public void writeCString(int address, String str, Charset charset) {
        final byte[] data = str.getBytes(charset);
        final ByteBuffer buf = buffer();
        buf.put(address, data);
        buf.put(address + data.length, (byte) 0);
    }

    /**
     * Reads a byte array of the specified length from the specified memory address.
     * 
     * @param address The starting address in WASM memory.
     * @param len     The number of bytes to read.
     * @return The byte array read from memory.
     */
    public byte[] read(int address, int len) {
        final byte[] result = new byte[len];
        buffer().get(address, result);
        return result;
    }

    /**
     * Reads a single byte from the specified memory address.
     * 
     * @param address The address in WASM memory.
     * @return The byte read from memory.
     */
    public byte readByte(int address) {
        return buffer().get(address);
    }

    /**
     * Reads a 32-bit integer from the specified memory address in little-endian
     * order.
     * 
     * @param address The starting address in WASM memory.
     * @return The integer value read from memory.
     */
    public int readInt(int address) {
        return buffer().getInt(address);
    }

    /**
     * Reads a 64-bit long from the specified memory address in little-endian order.
     * 
     * @param address The starting address in WASM memory.
     * @return The long value read from memory.
     */
    public long readLong(int address) {
        return buffer().getLong(address);
    }

    /**
     * Reads a 16-bit short from the specified memory address in little-endian
     * order.
     * 
     * @param address The starting address in WASM memory.
     * @return The short value read from memory.
     */
    public short readShort(int address) {
        return buffer().getShort(address);
    }

    /**
     * Reads a string of the specified length from memory using the given charset.
     * 
     * @param address The starting address in WASM memory.
     * @param len     The length of the string in bytes.
     * @param charset The charset to use for decoding.
     * @return The string decoded from memory.
     */
    public String readString(int address, int len, Charset charset) {
        final byte[] result = read(address, len);
        return new String(result, charset);
    }

    /**
     * Reads a null-terminated string (C-style string) from the specified memory
     * address.
     * 
     * @param address The starting address in WASM memory.
     * @param charset The charset to use for decoding.
     * @return The string decoded from memory.
     */
    public String readCString(int address, Charset charset) {
        final ByteBuffer buf = buffer();
        int end = address;
        while (buf.get(end) != 0) {
            end++;
        }
        final int len = end - address;
        final byte[] data = new byte[len];
        buf.get(address, data);
        return new String(data, charset);
    }

    /**
     * Grows the memory by the specified number of pages.
     * Note that, by default, a WebAssembly memory's page size is 64KiB (aka 65536
     * or 216).
     * 
     * @param delta The number of pages to add.
     */
    public void grow(long delta) {
        growMemory(instance.getInstancePtr(), store.getStorePtr(), this.name, delta);
        // Invalidate buffer to force refresh on next access
        this.buffer = null;
    }

    /**
     * 
     * Returns the WasmtimeInstance this memory belongs to.
     * @return The WasmtimeInstance associated with this memory.
     */
    public WasmtimeInstance getInstance() {
        return instance;
    }

    /**
     * Returns the WasmtimeStore associated with this memory.
     * @return The WasmtimeStore of this memory.
     */
    public WasmtimeStore getStore() {
        return store;
    }
}
