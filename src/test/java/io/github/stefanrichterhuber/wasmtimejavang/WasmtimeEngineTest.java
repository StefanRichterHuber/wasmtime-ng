package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

public class WasmtimeEngineTest {
    private static final Logger LOGGER = LogManager.getLogger();

    String wat = """
                    (module
              (func $hello (import "env" "hello"))
              (func (export "run") (call $hello))
            )

                    """;

    @Test
    public void testCreateEngine() throws Exception {
        try (WasmtimeEngine engine = new WasmtimeEngine()) {
        }
    }

    @Test
    public void testCreateModule() throws Exception {
        // Wasm module can be created from (WAT) string
        try (WasmtimeEngine engine = new WasmtimeEngine(); WasmtimeModule module = new WasmtimeModule(engine, wat)) {
        }

        // Wasm module can be created from direct ByteBuffer. Direct byte buffers can
        // be directly accessed in the wasm context -> more efficent
        byte[] watSrc = wat.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b1 = ByteBuffer.allocateDirect(watSrc.length);
        b1.put(watSrc);
        b1.flip();
        try (WasmtimeEngine engine = new WasmtimeEngine(); WasmtimeModule module = new WasmtimeModule(engine, b1)) {
        }

        // Wasm module can be created from ByteBuffer. Content will be copied!
        ByteBuffer b2 = ByteBuffer.allocate(watSrc.length);
        b2.put(watSrc);
        b2.flip();
        try (WasmtimeEngine engine = new WasmtimeEngine(); WasmtimeModule module = new WasmtimeModule(engine, b2)) {
        }

        // Wasm module can be created from InputStreams. Content will be copied!
        ByteArrayInputStream bis = new ByteArrayInputStream(watSrc);
        try (WasmtimeEngine engine = new WasmtimeEngine(); WasmtimeModule module = new WasmtimeModule(engine, bis)) {
        }

        // Wasm module can be created from ByteArray. Content will be copied!
        try (WasmtimeEngine engine = new WasmtimeEngine(); WasmtimeModule module = new WasmtimeModule(engine, watSrc)) {
        }
    }

    @Test
    public void testCreateStore() throws Exception {
        try (WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, wat);
                WasmtimeStore store = new WasmtimeStore(engine)) {
        }
    }

    @Test
    public void testCreateLinker() throws Exception {
        try (WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, wat);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);) {

            linker.importFunction("env", "hello", List.of(), List.of(), (instance, params) -> {
                return new Object[] { 0 };
            });
        }
    }

    @Test
    public void testCreateInstance() throws Exception {
        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, wat);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {
            // Add a java native function which the wasm runtime can call
            linker.importFunction("env", "hello", List.of(), List.of(), (instance, params) -> {
                LOGGER.info("Function env::hello called");
                return new Object[] { 0 };
            });

            // Call the exported function 'run'
            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
            }

        }
    }

    @Test
    public void testInvokeFunction() throws Exception {
        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, wat);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {
            store.getContext().put("greeting", "Hello world");
            linker.importFunction("env", "hello", List.of(), List.of(), (instance, params) -> {
                LOGGER.info("Function env::hello called with greeting: " + instance.getContext().get("greeting"));
                return new Object[] {};
            });

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                Object[] result = instance.invoke("run");
                assertNotNull(result);
            }

        }
    }

    /**
     * One can get any exported function as WasmtimeFunction object
     */
    @Test
    public void testExportFunction() throws Exception {
        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, wat);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {
            store.getContext().put("greeting", "Hello world");
            linker.importFunction("env", "hello", List.of(), List.of(), (instance, params) -> {
                LOGGER.info("Function env::hello called with greeting: " + instance.getContext().get("greeting"));
                return new Object[] {};
            });

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                WasmtimeFunction f = instance.getFunction("run");
                Object[] result = f.call(instance, store.getContext());
                assertNotNull(result);
            }

        }
    }

    @Test
    public void testJavaExceptionFunction() throws Exception {
        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, wat);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {
            linker.importFunction("env", "hello", List.of(), List.of(), (instance, params) -> {
                throw new IllegalStateException("This is a test exception");
            });

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                Object[] result = instance.invoke("run");
                fail("Exception must be thrown");
                assertNotNull(result);
            } catch (RuntimeException e) {
                // exception expected
            }

        }
    }

    @Test
    public void testPrecompile() throws Exception {
        byte[] precompiled = null;
        // One can precompile the code with one engine, store it somewhere and use it
        // with another engine
        try (
                WasmtimeEngine engine = new WasmtimeEngine();) {
            precompiled = engine.precompile(wat);
        }

        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = WasmtimeModule.fromPrecompiled(engine, precompiled);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {
            store.getContext().put("greeting", "Hello world");
            linker.importFunction("env", "hello", List.of(), List.of(), (instance, params) -> {
                LOGGER.info("Function env::hello called with greeting: " + instance.getContext().get("greeting"));
                return new Object[] {};
            });

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                Object[] result = instance.invoke("run");
                assertNotNull(result);
            }

        }

    }
}
