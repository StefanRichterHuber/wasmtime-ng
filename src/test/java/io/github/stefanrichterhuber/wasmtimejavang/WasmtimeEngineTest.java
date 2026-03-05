package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.List;

import org.junit.jupiter.api.Test;

public class WasmtimeEngineTest {

    @Test
    public void testCreateEngine() throws Exception {
        try (WasmtimeEngine engine = new WasmtimeEngine()) {
            System.out.println(engine);
        }
    }

    String wat = """
                    (module
              (func $hello (import "" "hello"))
              (func (export "run") (call $hello))
            )

                    """;

    @Test
    public void testCreateModule() throws Exception {
        try (WasmtimeEngine engine = new WasmtimeEngine(); WasmtimeModule module = new WasmtimeModule(engine, wat)) {
            System.out.println(engine);
        }
    }

    @Test
    public void testCreateStore() throws Exception {
        try (WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, wat);
                WasmtimeStore store = new WasmtimeStore(engine)) {
            System.out.println(engine);
        }
    }

    @Test
    public void testCreateLinker() throws Exception {
        try (WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, wat);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);) {

            linker.importFunction("hello", List.of(), List.of(), params -> {
                return new long[] { 0 };
            });
            System.out.println(engine);
        }
    }
}
