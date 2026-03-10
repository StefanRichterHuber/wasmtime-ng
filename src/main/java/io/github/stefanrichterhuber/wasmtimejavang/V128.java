package io.github.stefanrichterhuber.wasmtimejavang;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Representation of the WASM V128 value
 */
public final class V128 {
    /**
     * WASM memory is always little endian
     */
    private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
    private final byte[] parts;

    /**
     * Constructs a V128 from 16 byte values
     * 
     * @param parts byte array
     */
    public V128(final byte[] parts) {
        if (parts == null) {
            throw new NullPointerException("parts must not be null");
        }
        if (parts.length != 16) {
            throw new IllegalArgumentException("V128 needs to be byte[16]");
        }
        this.parts = parts;
    }

    /**
     * Constructs a V128 from 8 short values
     * 
     * @param parts short array
     */
    public V128(final short[] parts) {
        if (parts == null) {
            throw new NullPointerException("parts must not be null");
        }
        if (parts.length != 8) {
            throw new IllegalArgumentException("V128 needs to be short[8]");
        }
        final ByteBuffer b = ByteBuffer.allocate(16)
                .order(BYTE_ORDER);
        for (int i = 0; i < parts.length; i++) {
            b.putShort(parts[i]);
        }
        this.parts = b.array();
    }

    /**
     * Constructs a V128 from 4 int values
     * 
     * @param parts int array
     */
    public V128(final int[] parts) {
        if (parts == null) {
            throw new NullPointerException("parts must not be null");
        }
        if (parts.length != 4) {
            throw new IllegalArgumentException("V128 needs to be int[4]");
        }
        final ByteBuffer b = ByteBuffer.allocate(16)
                .order(BYTE_ORDER);
        for (int i = 0; i < parts.length; i++) {
            b.putInt(parts[i]);
        }
        this.parts = b.array();
    }

    /**
     * Constructs a V128 from two long values
     * 
     * @param parts long array
     */
    public V128(final long[] parts) {
        if (parts == null) {
            throw new NullPointerException("parts must not be null");
        }
        if (parts.length != 2) {
            throw new IllegalArgumentException("V128 needs to be long[2]");
        }
        final ByteBuffer b = ByteBuffer.allocate(16)
                .order(BYTE_ORDER);
        for (int i = 0; i < parts.length; i++) {
            b.putLong(parts[i]);
        }
        this.parts = b.array();
    }

    /**
     * Constructs a V128 from a BigInteger. The BigInteger is treated as a 128-bit
     * signed or unsigned integer.
     * 
     * @param value BigInteger value
     */
    public V128(final BigInteger value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        if (value.bitLength() > 128) {
            throw new IllegalArgumentException("value must be at most 128 bits");
        }
        final byte[] bytes = value.toByteArray();
        this.parts = new byte[16];
        // BigInteger.toByteArray() returns big-endian.
        // We want to store it as little-endian in parts.
        final int n = Math.min(bytes.length, 16);
        for (int i = 0; i < n; i++) {
            this.parts[i] = bytes[bytes.length - 1 - i];
        }
        // Sign extension for negative numbers
        if (value.signum() < 0) {
            for (int i = n; i < 16; i++) {
                this.parts[i] = (byte) 0xff;
            }
        }
    }

    /**
     * Converts this V128 into an array of 16 byte values
     * 
     * @return byte array
     */
    public byte[] getBytes() {
        return parts;
    }

    /**
     * Converts this V128 into an array of 8 short values
     * 
     * @return short array
     */
    public short[] getShorts() {
        final ByteBuffer b = ByteBuffer.wrap(getBytes());
        b.order(BYTE_ORDER);
        return new short[] {
                b.getShort(0 * Short.BYTES),
                b.getShort(1 * Short.BYTES),
                b.getShort(2 * Short.BYTES),
                b.getShort(3 * Short.BYTES),
                b.getShort(4 * Short.BYTES),
                b.getShort(5 * Short.BYTES),
                b.getShort(6 * Short.BYTES),
                b.getShort(7 * Short.BYTES)
        };
    }

    /**
     * Converts this V128 into an array of 4 int values
     * 
     * @return int array
     */
    public int[] getInts() {
        final ByteBuffer b = ByteBuffer.wrap(getBytes());
        b.order(BYTE_ORDER);
        return new int[] {
                b.getInt(0 * Integer.BYTES),
                b.getInt(1 * Integer.BYTES),
                b.getInt(2 * Integer.BYTES),
                b.getInt(3 * Integer.BYTES)
        };
    }

    /**
     * Converts this V128 into an array of 2 long values
     * 
     * @return long array
     */
    public long[] getLongs() {
        final ByteBuffer b = ByteBuffer.wrap(getBytes());
        b.order(BYTE_ORDER);

        return new long[] {
                b.getLong(0 * Long.BYTES),
                b.getLong(1 * Long.BYTES)
        };
    }

    /**
     * Converts this V128 into a BigInteger. The bytes are treated as a 128-bit
     * signed integer.
     * 
     * @return BigInteger
     */
    public BigInteger getBigInteger() {
        final byte[] bigEndian = new byte[16];
        for (int i = 0; i < 16; i++) {
            bigEndian[i] = parts[15 - i];
        }
        return new BigInteger(bigEndian);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other instanceof V128 v) {
            return Arrays.equals(this.parts, v.parts);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(parts);
    }

}
