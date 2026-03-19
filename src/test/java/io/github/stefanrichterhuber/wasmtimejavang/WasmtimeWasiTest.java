package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.wasmtimejavang.wasip1.LoggerOutputStream;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.ProcExitException;

public class WasmtimeWasiTest {
    private static final String WASM_PATH = "target/rust-test/wasip1test/wasm32-wasip1/debug/wasip1test.wasm";

    @Test
    public void wasip1test() throws Exception {
        try (
                FileInputStream fis = new FileInputStream(WASM_PATH);
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, fis);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);

        ) {
            linker.linkContext(new WasiPI1Context()
                    .withEnvs(Map.of("FOO", "BAR", "BAZ", "QUX"))
                    .withArguments(List.of("wasip1test.wasm", "hello", "world"))
                    .withStdOut(new LoggerOutputStream("wasip1test", Level.INFO))
                    .withStdErr(new LoggerOutputStream("wasip1test", Level.ERROR)));
            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                Object[] result = instance.start();
                assertNotNull(result);
            } catch (ProcExitException e) {
                if (e.getCode() != 0) {
                    throw e;
                }
            }

        }
    }
}
