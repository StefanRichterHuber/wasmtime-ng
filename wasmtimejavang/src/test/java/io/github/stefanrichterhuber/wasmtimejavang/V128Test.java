package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

public class V128Test {

    private static final BigInteger MIN_VALUE = BigInteger.ONE.shiftLeft(127).negate(); // -2^127
    private static final BigInteger MAX_VALUE = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE); // 2^128 - 1

    // --- byte[] constructor ---

    @Test
    public void byteArrayConstructorStoresBytes() {
        final byte[] bytes = new byte[16];
        for (int i = 0; i < 16; i++) {
            bytes[i] = (byte) (i + 1);
        }
        final V128 v = new V128(bytes);
        assertArrayEquals(bytes, v.getBytes());
    }

    @Test
    public void byteArrayConstructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new V128((byte[]) null));
    }

    @Test
    public void byteArrayConstructorRejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> new V128(new byte[15]));
        assertThrows(IllegalArgumentException.class, () -> new V128(new byte[17]));
        assertThrows(IllegalArgumentException.class, () -> new V128(new byte[0]));
    }

    // --- short[] constructor ---

    @Test
    public void shortArrayConstructorRoundTrips() {
        final short[] shorts = { 1, -2, 3, -4, 5, -6, 7, -8 };
        final V128 v = new V128(shorts);
        assertArrayEquals(shorts, v.getShorts());
    }

    @Test
    public void shortArrayConstructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new V128((short[]) null));
    }

    @Test
    public void shortArrayConstructorRejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> new V128(new short[7]));
        assertThrows(IllegalArgumentException.class, () -> new V128(new short[9]));
    }

    // --- int[] constructor ---

    @Test
    public void intArrayConstructorRoundTrips() {
        final int[] ints = { 1, -2, Integer.MAX_VALUE, Integer.MIN_VALUE };
        final V128 v = new V128(ints);
        assertArrayEquals(ints, v.getInts());
    }

    @Test
    public void intArrayConstructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new V128((int[]) null));
    }

    @Test
    public void intArrayConstructorRejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> new V128(new int[3]));
        assertThrows(IllegalArgumentException.class, () -> new V128(new int[5]));
    }

    // --- long[] constructor ---

    @Test
    public void longArrayConstructorRoundTrips() {
        final long[] longs = { 5L, 0L };
        final V128 v = new V128(longs);
        assertArrayEquals(longs, v.getLongs());
    }

    @Test
    public void longArrayConstructorAllOnesIsMinusOne() {
        final V128 v = new V128(new long[] { -1L, -1L });
        assertEquals(BigInteger.valueOf(-1), v.getBigInteger());
    }

    @Test
    public void longArrayConstructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new V128((long[]) null));
    }

    @Test
    public void longArrayConstructorRejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> new V128(new long[1]));
        assertThrows(IllegalArgumentException.class, () -> new V128(new long[3]));
    }

    // --- BigInteger constructor ---

    @Test
    public void bigIntegerConstructorRoundTripsPositive() {
        final BigInteger value = BigInteger.valueOf(123456789012345L);
        final V128 v = new V128(value);
        assertEquals(value, v.getBigInteger());
    }

    @Test
    public void bigIntegerConstructorRoundTripsNegative() {
        final BigInteger value = BigInteger.valueOf(-42L);
        final V128 v = new V128(value);
        assertEquals(value, v.getBigInteger());
    }

    @Test
    public void bigIntegerConstructorRoundTripsZero() {
        final V128 v = new V128(BigInteger.ZERO);
        assertEquals(BigInteger.ZERO, v.getBigInteger());
        assertArrayEquals(new byte[16], v.getBytes());
    }

    @Test
    public void bigIntegerConstructorAcceptsSignedMinBoundary() {
        // -2^127 is the smallest value representable as a 128-bit signed integer.
        final V128 v = new V128(MIN_VALUE);
        assertEquals(MIN_VALUE, v.getBigInteger());
    }

    @Test
    public void bigIntegerConstructorAcceptsUnsignedMaxBoundary() {
        // 2^128 - 1 (all bits set) is the largest value representable as a 128-bit
        // unsigned integer; read back as signed, this is -1.
        final V128 v = new V128(MAX_VALUE);
        assertEquals(BigInteger.valueOf(-1), v.getBigInteger());
        assertArrayEquals(new byte[] {
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 }, v.getBytes());
    }

    @Test
    public void bigIntegerConstructorRejectsBelowSignedMin() {
        // Regression test: values below -2^127 must be rejected rather than silently
        // truncated. BigInteger#bitLength() alone is not a sufficient range check for
        // negative numbers since it excludes the sign bit, previously allowing values
        // as low as -2^128 to pass validation and be corrupted on construction.
        assertThrows(IllegalArgumentException.class, () -> new V128(MIN_VALUE.subtract(BigInteger.ONE)));
        assertThrows(IllegalArgumentException.class,
                () -> new V128(BigInteger.ONE.shiftLeft(128).negate())); // -2^128
    }

    @Test
    public void bigIntegerConstructorRejectsAboveUnsignedMax() {
        assertThrows(IllegalArgumentException.class, () -> new V128(MAX_VALUE.add(BigInteger.ONE)));
    }

    @Test
    public void bigIntegerConstructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new V128((BigInteger) null));
    }

    // --- cross-representation consistency ---

    @Test
    public void allRepresentationsAgreeForSameBitPattern() {
        final long[] longs = { 0x0102030405060708L, -1L };
        final V128 v = new V128(longs);

        final V128 fromBytes = new V128(v.getBytes());
        final V128 fromShorts = new V128(v.getShorts());
        final V128 fromInts = new V128(v.getInts());
        final V128 fromBigInteger = new V128(v.getBigInteger());

        assertEquals(v, fromBytes);
        assertEquals(v, fromShorts);
        assertEquals(v, fromInts);
        assertEquals(v, fromBigInteger);
    }

    // --- equals / hashCode ---

    @Test
    public void equalsAndHashCodeForEqualValues() {
        final V128 a = new V128(BigInteger.valueOf(7));
        final V128 b = new V128(new long[] { 7L, 0L });
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalsIsFalseForDifferentValues() {
        final V128 a = new V128(BigInteger.valueOf(7));
        final V128 b = new V128(BigInteger.valueOf(8));
        assertNotEquals(a, b);
    }

    @Test
    public void equalsHandlesSelfNullAndOtherType() {
        final V128 a = new V128(BigInteger.valueOf(7));
        assertTrue(a.equals(a));
        assertFalse(a.equals(null));
        assertFalse(a.equals("not a V128"));
    }

    // --- Number conversions ---

    @Test
    public void numberConversionsMatchBigInteger() {
        final long value = 123456789012345L;
        final V128 v = new V128(BigInteger.valueOf(value));
        assertEquals(value, v.longValue());
        assertEquals((int) value, v.intValue());
        assertEquals((float) value, v.floatValue());
        assertEquals((double) value, v.doubleValue());
    }

    @Test
    public void numberConversionsForNegativeValue() {
        final V128 v = new V128(BigInteger.valueOf(-1));
        assertEquals(-1L, v.longValue());
        assertEquals(-1, v.intValue());
        assertEquals(-1.0f, v.floatValue());
        assertEquals(-1.0d, v.doubleValue());
    }

    // --- compareTo ---

    @Test
    public void compareToOrdersBySignedValue() {
        final V128 negative = new V128(BigInteger.valueOf(-1));
        final V128 zero = new V128(BigInteger.ZERO);
        final V128 positive = new V128(BigInteger.valueOf(1));

        assertTrue(negative.compareTo(zero) < 0);
        assertTrue(zero.compareTo(positive) < 0);
        assertTrue(positive.compareTo(negative) > 0);
        assertEquals(0, zero.compareTo(new V128(BigInteger.ZERO)));
    }
}
