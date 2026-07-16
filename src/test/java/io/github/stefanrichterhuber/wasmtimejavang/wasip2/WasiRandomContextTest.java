package io.github.stefanrichterhuber.wasmtimejavang.wasip2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext.ComponentImportFunction;

public class WasiRandomContextTest {

    @Test
    public void nameProvidedInterfacesAndDependencies() {
        WasiRandomContext random = new WasiRandomContext();
        assertEquals("wasi-random", random.name());
        assertEquals(WasiRandomContext.NAME, random.name());
        assertTrue(random.getProvidedInterfaces().contains("wasi:random/random@0.2.6"));
        assertTrue(random.getProvidedInterfaces().contains("wasi:random/insecure@0.2.6"));
        assertTrue(random.getProvidedInterfaces().contains("wasi:random/insecure-seed@0.2.6"));
        assertTrue(random.getDependencies().isEmpty());
    }

    @Test
    public void importFunctionsCoverEveryDeclaredMethod() {
        WasiRandomContext random = new WasiRandomContext();
        List<ComponentImportFunction> functions = random.getImportFunctions();

        assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals("wasi:random/random@0.2.6")
                && f.funcName().equals("get-random-bytes")));
        assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals("wasi:random/random@0.2.6")
                && f.funcName().equals("get-random-u64")));
        assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals("wasi:random/insecure@0.2.6")
                && f.funcName().equals("get-insecure-random-bytes")));
        assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals("wasi:random/insecure@0.2.6")
                && f.funcName().equals("get-insecure-random-u64")));
        assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals("wasi:random/insecure-seed@0.2.6")
                && f.funcName().equals("insecure-seed")));
    }

    @Test
    public void getRandomBytesReturnsBytesOfCorrectLength() {
        WasiRandomContext random = new WasiRandomContext();
        Object[] result = random.getRandomBytes(null, new Object[] { 16L });
        byte[] bytes = (byte[]) result[0];
        assertEquals(16, bytes.length);
    }

    @Test
    public void getRandomU64ReturnsValue() {
        WasiRandomContext random = new WasiRandomContext();
        Object[] result = random.getRandomU64(null, new Object[0]);
        Long val = (Long) result[0];
        assertNotNull(val);
    }

    @Test
    public void getInsecureRandomBytesReturnsBytesOfCorrectLength() {
        WasiRandomContext random = new WasiRandomContext();
        Object[] result = random.getInsecureRandomBytes(null, new Object[] { 32L });
        byte[] bytes = (byte[]) result[0];
        assertEquals(32, bytes.length);
    }

    @Test
    public void getInsecureRandomU64ReturnsValue() {
        WasiRandomContext random = new WasiRandomContext();
        Object[] result = random.getInsecureRandomU64(null, new Object[0]);
        Long val = (Long) result[0];
        assertNotNull(val);
    }

    @Test
    public void insecureSeedReturnsTuple() {
        WasiRandomContext random = new WasiRandomContext();
        Object[] result = random.insecureSeed(null, new Object[0]);
        Object[] tuple = (Object[]) result[0];
        assertEquals(2, tuple.length);
        assertTrue(tuple[0] instanceof Long);
        assertTrue(tuple[1] instanceof Long);
    }

    @Test
    public void usesCustomRandomGenerators() {
        byte[] fixedBytes = new byte[8];
        fixedBytes[0] = 42;
        
        SecureRandom mockSecure = new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                System.arraycopy(fixedBytes, 0, bytes, 0, Math.min(fixedBytes.length, bytes.length));
            }
        };

        Random mockInsecure = new Random() {
            @Override
            public void nextBytes(byte[] bytes) {
                System.arraycopy(fixedBytes, 0, bytes, 0, Math.min(fixedBytes.length, bytes.length));
            }

            @Override
            public long nextLong() {
                return 999L;
            }
        };

        WasiRandomContext random = new WasiRandomContext(mockSecure, mockInsecure);
        
        byte[] secureBytes = (byte[]) random.getRandomBytes(null, new Object[]{8L})[0];
        assertEquals(42, secureBytes[0]);

        byte[] insecureBytes = (byte[]) random.getInsecureRandomBytes(null, new Object[]{8L})[0];
        assertEquals(42, insecureBytes[0]);

        assertEquals(999L, random.getInsecureRandomU64(null, new Object[0])[0]);
    }

    @Test
    public void implementsWasmComponentContext() {
        assertTrue(WasmComponentContext.class.isAssignableFrom(WasiRandomContext.class));
    }
}
