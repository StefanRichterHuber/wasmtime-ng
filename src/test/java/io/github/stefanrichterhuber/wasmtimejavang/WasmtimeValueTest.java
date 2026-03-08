package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.FileInputStream;
import java.util.List;

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
                    List<Object> result = instance.invoke("add_i32", List.of(10, 20));
                    assertNotNull(result);
                    assertEquals(1, result.size());
                    assertEquals(30, result.get(0));
                    assertEquals(Integer.class, result.get(0).getClass());
                }

                // I64
                {
                    List<Object> result = instance.invoke("add_i64", List.of(10L, 20L));
                    assertNotNull(result);
                    assertEquals(1, result.size());
                    assertEquals(30L, result.get(0));
                    assertEquals(Long.class, result.get(0).getClass());
                }

                // F32
                {
                    List<Object> result = instance.invoke("add_f32", List.of(3.14f, 2.71f));
                    assertNotNull(result);
                    assertEquals(1, result.size());
                    Object val = result.get(0);
                    assertEquals(Float.class, val.getClass());
                    assertEquals(3.14f + 2.71f, (Float) val, 0.001f);
                }

                // F64
                {
                    List<Object> result = instance.invoke("add_f64", List.of(3.14159, 2.71828));
                    assertNotNull(result);
                    assertEquals(1, result.size());
                    Object val = result.get(0);
                    assertEquals(Double.class, val.getClass());
                    assertEquals(3.14159 + 2.71828, (Double) val, 0.00001);
                }
            }
        }
    }
}
