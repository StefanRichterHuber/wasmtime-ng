package io.github.stefanrichterhuber.wasmtimejavang;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public interface WasmtimeMemory {

    /**
     * Returns a ByteBuffer view of the WASM memory.
     * The buffer is cached and refreshed if the memory size changes.
     * 
     * Warning: Do not cache this reference, but fetch it everytime prior to use!
     * 
     * @return A ByteBuffer mapping the WASM memory.
     */
    ByteBuffer buffer();

    /**
     * Checks if this is a shared memory
     * 
     * @return
     */
    boolean isShared();

    /**
     * Writes a byte array to the specified memory address.
     * 
     * @param address The starting address in WASM memory.
     * @param data    The byte array to write.
     * @returns Number of bytes written
     */
    default int write(int address, byte[] data) {
        buffer().put(address, data);
        return data.length;
    }

    /**
     * Writes a single byte to the specified memory address.
     * 
     * @param address The address in WASM memory.
     * @param data    The byte to write.
     * @returns Number of bytes written
     */
    default int write(int address, byte data) {
        buffer().put(address, data);
        return Byte.BYTES;
    }

    /**
     * Writes a 16-bit short to the specified memory address in little-endian order.
     * 
     * @param address The starting address in WASM memory.
     * @param value   The short value to write.
     * @returns Number of bytes written
     */
    default int writeShort(int address, short value) {
        buffer().putShort(address, value);
        return Short.BYTES;
    }

    /**
     * Writes a 32-bit integer to the specified memory address in little-endian
     * order.
     * 
     * @param address The starting address in WASM memory.
     * @param value   The integer value to write.
     * @returns Number of bytes written
     */
    default int writeInt(int address, int value) {
        buffer().putInt(address, value);
        return Integer.BYTES;
    }

    /**
     * Writes a 64-bit long to the specified memory address in little-endian order.
     * 
     * @param address The starting address in WASM memory.
     * @param value   The long value to write.
     * @returns Number of bytes written
     */
    default int writeLong(int address, long value) {
        buffer().putLong(address, value);
        return Long.BYTES;
    }

    /**
     * Writes a string to the specified memory address using the given charset.
     * 
     * @param address The starting address in WASM memory.
     * @param str     The string to write.
     * @param charset The charset to use for encoding.
     * @returns Number of bytes written
     */
    default int writeString(int address, String str, Charset charset) {
        final byte[] buf = str.getBytes(charset);
        this.write(address, buf);
        return buf.length;
    }

    /**
     * Writes a null-terminated string (C-style string) to the specified memory
     * address.
     * 
     * @param address The starting address in WASM memory.
     * @param str     The string to write.
     * @param charset The charset to use for encoding.
     * @returns Number of bytes written
     */
    default int writeCString(int address, String str, Charset charset) {
        final byte[] data = str.getBytes(charset);
        final ByteBuffer buf = buffer();
        buf.put(address, data);
        buf.put(address + data.length, (byte) 0);
        return data.length + 1; // +1 for null termination
    }

    /**
     * Reads a byte array of the specified length from the specified memory address.
     * 
     * @param address The starting address in WASM memory.
     * @param len     The number of bytes to read.
     * @return The byte array read from memory.
     */
    default byte[] read(int address, int len) {
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
    default byte readByte(int address) {
        return buffer().get(address);
    }

    /**
     * Reads a 32-bit integer from the specified memory address in little-endian
     * order.
     * 
     * @param address The starting address in WASM memory.
     * @return The integer value read from memory.
     */
    default int readInt(int address) {
        return buffer().getInt(address);
    }

    /**
     * Reads a 64-bit long from the specified memory address in little-endian order.
     * 
     * @param address The starting address in WASM memory.
     * @return The long value read from memory.
     */
    default long readLong(int address) {
        return buffer().getLong(address);
    }

    /**
     * Reads a 16-bit short from the specified memory address in little-endian
     * order.
     * 
     * @param address The starting address in WASM memory.
     * @return The short value read from memory.
     */
    default short readShort(int address) {
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
    default String readString(int address, int len, Charset charset) {
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
    default String readCString(int address, Charset charset) {
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
    void grow(long delta);

}
