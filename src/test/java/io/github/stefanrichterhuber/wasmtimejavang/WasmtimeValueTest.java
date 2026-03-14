package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public class WasmtimeValueTest {
    private static final String WASM_PATH = "target/rust-test/valuetest/wasm32-wasip1/debug/valuetest.wasm";

    @Test
    public void testValues() throws Exception {
        try (
                FileInputStream fis = new FileInputStream(WASM_PATH);
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, fis);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {
            linker.link(new WasiPI1Context());

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                // I32
                {
                    Object[] result = instance.invoke("add_i32", new Object[] { 10, 20 });
                    assertNotNull(result);
                    assertEquals(1, result.length);
                    assertEquals(30, result[0]);
                    assertEquals(Integer.class, result[0].getClass());
                }

                // I64
                {
                    Object[] result = instance.invoke("add_i64", new Object[] { 10L, 20L });
                    assertNotNull(result);
                    assertEquals(1, result.length);
                    assertEquals(30L, result[0]);
                    assertEquals(Long.class, result[0].getClass());
                }

                // F32
                {
                    Object[] result = instance.invoke("add_f32", new Object[] { 3.14f, 2.71f });
                    assertNotNull(result);
                    assertEquals(1, result.length);
                    Object val = result[0];
                    assertEquals(Float.class, val.getClass());
                    assertEquals(3.14f + 2.71f, (Float) val, 0.001f);
                }

                // F64
                {
                    Object[] result = instance.invoke("add_f64", new Object[] { 3.14159, 2.71828 });
                    assertNotNull(result);
                    assertEquals(1, result.length);
                    Object val = result[0];
                    assertEquals(Double.class, val.getClass());
                    assertEquals(3.14159 + 2.71828, (Double) val, 0.00001);
                }
            }
        }
    }

    private static final String WAT_F32_TEST = """
                        (module
              ;; Import a function named "imported_func" from the "env" namespace.
              ;; It takes one f32 parameter and returns one f32 result.
              (import "env" "imported_func" (func $imported_func (param f32) (result f32)))

              ;; Define a function and export it as "exported_func".
              ;; It takes one f32 parameter (named $arg) and returns one f32 result.
              (func $exported_func (export "exported_func") (param $arg f32) (result f32)

                ;; 1. Push the f32 parameter onto the stack.
                local.get $arg

                ;; 2. Call the imported function.
                ;; This pops the f32 argument off the stack, executes the function,
                ;; and pushes the f32 result back onto the stack.
                call $imported_func

                ;; 3. The value left on the stack is implicitly returned.
              )
            )
                        """;

    /**
     * On can use float as function parameter and results
     * 
     * @throws Exception
     */
    @Test
    public void testWatF32Test() throws Exception {
        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, WAT_F32_TEST);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {
            store.getContext().put("greeting", "Hello world");
            linker.importFunction("env", "imported_func", List.of(ValType.F32), List.of(ValType.F32),
                    (instance, params) -> {
                        float p = (float) params[0];
                        float r = p * 3f;
                        return new Object[] { r };
                    });

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                Object[] result = instance.invoke("exported_func", 6f);
                assertNotNull(result);
                assertEquals(1, result.length);
                assertInstanceOf(Float.class, result[0]);
                assertEquals(18f, (float) result[0], 0.001f);
            }
        }
    }

    /**
     * On can use double as function parameter and results
     * 
     * @throws Exception
     */
    @Test
    public void testWatF64Test() throws Exception {
        String modifiedWat = WAT_F32_TEST.replaceAll("f32", "f64");

        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, modifiedWat);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {
            store.getContext().put("greeting", "Hello world");
            linker.importFunction("env", "imported_func", List.of(ValType.F64), List.of(ValType.F64),
                    (instance, params) -> {
                        double p = (double) params[0];
                        double r = p * 3d;
                        return new Object[] { r };
                    });

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                Object[] result = instance.invoke("exported_func", 6d);
                assertNotNull(result);
                assertEquals(1, result.length);
                assertInstanceOf(Double.class, result[0]);
                assertEquals(18f, (Double) result[0], 0.001f);
            }
        }
    }

    /**
     * On can use int as function parameter and results
     * 
     * @throws Exception
     */
    @Test
    public void testWatI32Test() throws Exception {
        String modifiedWat = WAT_F32_TEST.replaceAll("f32", "i32");

        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, modifiedWat);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {
            store.getContext().put("greeting", "Hello world");
            linker.importFunction("env", "imported_func", List.of(ValType.I32), List.of(ValType.I32),
                    (instance, params) -> {
                        int p = (int) params[0];
                        int r = p * 3;
                        return new Object[] { r };
                    });

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                Object[] result = instance.invoke("exported_func", (int) 6);
                assertNotNull(result);
                assertEquals(1, result.length);
                assertInstanceOf(Integer.class, result[0]);
                assertEquals(18, (Integer) result[0]);
            }
        }
    }

    /**
     * On can use long as function parameter and results
     * 
     * @throws Exception
     */
    @Test
    public void testWatI64Test() throws Exception {
        String modifiedWat = WAT_F32_TEST.replaceAll("f32", "i64");

        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, modifiedWat);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {
            store.getContext().put("greeting", "Hello world");
            linker.importFunction("env", "imported_func", List.of(ValType.I64), List.of(ValType.I64),
                    (instance, params) -> {
                        long p = (long) params[0];
                        long r = p * 3l;
                        return new Object[] { r };
                    });

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                Object[] result = instance.invoke("exported_func", (int) 6);
                assertNotNull(result);
                assertEquals(1, result.length);
                assertInstanceOf(Long.class, result[0]);
                assertEquals(18, (Long) result[0]);
            }
        }
    }

    public static final String WAT_FUNCREF_TEST = """
                        (module
              ;; 1. Import a function from the host environment (e.g., JavaScript).
              (import "env" "imported_func" (func $imported_func))

              ;; 2. Declare the internal function so a reference to it can be created.
              ;; This is required by the WebAssembly Reference Types specification.
              (elem declare func $internal_func)

              ;; 3. Define the internal function.
              ;; This function calls the imported function.
              (func $internal_func
                call $imported_func
              )

              ;; 4. Define and export the function that returns the FuncRef.
              ;; It has a result type of `funcref`.
              (func $get_func_ref (export "get_func_ref") (result funcref)

                ;; Push the reference to the internal function onto the stack.
                ref.func $internal_func

                ;; The funcref on the stack is implicitly returned.
              )
            )
                        """;

    /**
     * One can return Function References from the wasm object
     * 
     * @throws Exception
     */
    @Test
    public void testWatFuncRefTest() throws Exception {

        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, WAT_FUNCREF_TEST);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {
            store.getContext().put("greeting", "Hello world");
            linker.importFunction("env", "imported_func", List.of(), List.of(),
                    (instance, params) -> {
                        return new Object[] {};
                    });

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                Object[] result = instance.invoke("get_func_ref");
                assertNotNull(result);
                assertEquals(1, result.length);
                assertInstanceOf(WasmtimeFunction.class, result[0]);

                WasmtimeFunction f = (WasmtimeFunction) result[0];
                Object[] callResult = f.call(instance, store.getContext());
                assertNotNull(callResult);
            }
        }
    }

    public static final String WAT_EXTERNREF_STRING = """
                        (module
              ;; 1. Import a function from the host environment.
              ;; It expects exactly one parameter of type `externref`.
              (import "env" "imported_func" (func $imported_func (param externref)))

              ;; 2. Define a function and export it.
              ;; It takes one `externref` parameter named $ext_ref.
              (func $exported_func (export "exported_func") (param $ext_ref externref) (result externref)

                ;; Push the externref onto the top of the stack.
                local.get $ext_ref

                ;; Call the imported function.
                ;; This pops the externref off the stack and uses it as the argument.
                call $imported_func

                ;; Push the externref onto the stack one more time.
                ;; Since this is the last item left on the stack at the end of the function,
                ;; WebAssembly uses it as the return value.
                local.get $ext_ref
              )
            )
                        """;

    /**
     * One can passe any java value through the wasm code as extern reference
     * 
     * @throws Exception
     */
    @Test
    public void testWatExternRefTest() throws Exception {

        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, WAT_EXTERNREF_STRING);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {
            AtomicInteger externRefValue = new AtomicInteger(0);

            store.getContext().put("greeting", "Hello world");
            linker.importFunction("env", "imported_func", List.of(ValType.Ref), List.of(),
                    (instance, params) -> {
                        AtomicInteger ai = (AtomicInteger) params[0];
                        ai.incrementAndGet();

                        return new Object[] {};
                    });

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {

                assertEquals(0, externRefValue.get());
                Object[] result = instance.invoke("exported_func", externRefValue);
                assertNotNull(result);
                assertEquals(1, result.length);
                assertInstanceOf(AtomicInteger.class, result[0]);
                assertTrue(result[0] == externRefValue);
                assertEquals(1, externRefValue.get());

            }
        }
    }

    public static final String WAT_V128_INT_STRING = """
                              (module
              ;; Define the exported function.
              ;; It accepts one v128 parameter and returns a v128 result.
              (func $multiply_by_two (export "multiply_by_two") (param $vec v128) (result v128)

                ;; 1. Push the input vector onto the stack.
                local.get $vec

                ;; 2. Push a constant vector of 2s onto the stack.
                ;; We format this as four 32-bit integers, all set to 2.
                v128.const i32x4 2 2 2 2

                ;; 3. Multiply the top two vectors on the stack.
                ;; i32x4.mul multiplies the corresponding lanes of both vectors together.
                i32x4.mul

                ;; The resulting v128 vector is left on the stack and implicitly returned.
              )
            )
                                    """;

    /**
     * V128 values can be mapped to / from bytes / short / int / longs
     * 
     * @throws Exception
     */
    @Test
    public void testWatV128IntTest() throws Exception {

        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, WAT_V128_INT_STRING);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {

                int multiplier = 2;
                int[] values = { 4, 3, 2, 1 };
                int[] expectedResult = { 4 * multiplier, 3 * multiplier, 2 * multiplier, 1 * multiplier };

                V128 v = new V128(values);
                int testValues[] = v.getInts();
                assertArrayEquals(values, testValues);

                Object[] result = instance.invoke("multiply_by_two", v);
                assertNotNull(result);
                assertEquals(1, result.length);
                assertInstanceOf(V128.class, result[0]);
                V128 resultV = (V128) result[0];

                assertArrayEquals(expectedResult, resultV.getInts());
            }
        }
    }

    public static final String WAT_V128_LONG_STRING = """
                              (module
              ;; Define the exported function.
              ;; It accepts one v128 parameter and returns a v128 result.
              (func $multiply_by_two (export "multiply_by_two") (param $vec v128) (result v128)

                ;; 1. Push the input vector onto the stack.
                local.get $vec

                ;; 2. Push a constant vector of 2s onto the stack.
                ;; We format this as 2 64-bit integers, all set to 2.
                v128.const i64x2 2 2

                ;; 3. Multiply the top two vectors on the stack.
                ;; i32x4.mul multiplies the corresponding lanes of both vectors together.
                i64x2.mul

                ;; The resulting v128 vector is left on the stack and implicitly returned.
              )
            )
                                    """;

    /**
     * V128 values can be mapped to / from bytes / short / int / longs
     * 
     * @throws Exception
     */
    @Test
    public void testWatV128LongTest() throws Exception {

        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, WAT_V128_INT_STRING);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                int multiplier = 2;
                long[] values = { 4, 3, };
                long[] expectedResult = { 4 * multiplier, 3 * multiplier, };

                V128 v = new V128(values);
                long testValues[] = v.getLongs();
                assertArrayEquals(values, testValues);

                Object[] result = instance.invoke("multiply_by_two", v);
                assertNotNull(result);
                assertEquals(1, result.length);
                assertInstanceOf(V128.class, result[0]);
                V128 resultV = (V128) result[0];

                assertArrayEquals(expectedResult, resultV.getLongs());
            }
        }
    }

    public static final String WAT_V128_SHORT_STRING = """
                              (module
              ;; Define the exported function.
              ;; It accepts one v128 parameter and returns a v128 result.
              (func $multiply_by_two (export "multiply_by_two") (param $vec v128) (result v128)

                ;; 1. Push the input vector onto the stack.
                local.get $vec

                ;; 2. Push a constant vector of 2s onto the stack.
                ;; We format this as four 32-bit integers, all set to 2.
                v128.const i16x8 2 2 2 2 2 2 2 2

                ;; 3. Multiply the top two vectors on the stack.
                ;; i32x4.mul multiplies the corresponding lanes of both vectors together.
                i16x8.mul

                ;; The resulting v128 vector is left on the stack and implicitly returned.
              )
            )
                                    """;

    /**
     * V128 values can be mapped to / from bytes / short / int / longs
     * 
     * @throws Exception
     */
    @Test
    public void testWatV128ShortTest() throws Exception {

        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, WAT_V128_INT_STRING);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {

                short multiplier = 2;
                short[] values = { 8, 7, 6, 5, 4, 3, 2, 1 };
                short[] expectedResult = {
                        (short) (8 * multiplier),
                        (short) (7 * multiplier),
                        (short) (6 * multiplier),
                        (short) (5 * multiplier),
                        (short) (4 * multiplier),
                        (short) (3 * multiplier),
                        (short) (2 * multiplier),
                        (short) (1 * multiplier) };

                V128 v = new V128(values);
                short testValues[] = v.getShorts();
                assertArrayEquals(values, testValues);

                Object[] result = instance.invoke("multiply_by_two", v);
                assertNotNull(result);
                assertEquals(1, result.length);
                assertInstanceOf(V128.class, result[0]);
                V128 resultV = (V128) result[0];

                assertArrayEquals(expectedResult, resultV.getShorts());
            }
        }
    }

    @Test
    public void testWatV128BigIntTest() throws Exception {

        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, WAT_V128_INT_STRING);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {

                BigInteger bi = new BigInteger("248248248248");
                V128 v = new V128(bi);
                BigInteger result = v.getBigInteger();
                assertEquals(bi, result);

                // Verify byte order consistency with int[]
                // 248248248248 = 0x39CCBFA7B8
                // Lane 0: 0xCCBFA7B8 = -859854920, Lane 1: 0x39 = 57
                int[] expectedInts = { -859854920, 57, 0, 0 };
                assertArrayEquals(expectedInts, v.getInts());

                Object[] results = instance.invoke("multiply_by_two", v);
                assertNotNull(results);
                assertEquals(1, results.length);
                assertInstanceOf(V128.class, results[0]);
                V128 resultV = (V128) results[0];

                // Lane 0: 0x997F4F70 = -1719709840, Lane 1: 0x72 = 114
                int[] expectedResultInts = { -1719709840, 114, 0, 0 };
                assertArrayEquals(expectedResultInts, resultV.getInts());
            }
        }
    }
}
