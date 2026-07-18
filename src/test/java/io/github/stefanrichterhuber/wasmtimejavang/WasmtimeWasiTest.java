package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
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
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
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
                    .withStdOut(bos)
                    .withStdErr(new LoggerOutputStream("wasip1test", Level.ERROR)));
            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                Object[] result = instance.start();
                assertNotNull(result);
            } catch (ProcExitException e) {
                if (e.getCode() != 0) {
                    throw e;
                }
            }

            /*
             * 
             * 10:09:31.575 [main] INFO wasip1test - Hello, WASI!
             * 10:09:31.575 [main] INFO wasip1test - Arguments:
             * 10:09:31.575 [main] INFO wasip1test - Arg 0: wasip1test.wasm
             * 10:09:31.575 [main] INFO wasip1test - Arg 1: hello
             * 10:09:31.576 [main] INFO wasip1test - Arg 2: world
             * 10:09:31.576 [main] INFO wasip1test - Environment variables:
             * 10:09:31.576 [main] INFO wasip1test - FOO: BAR
             * 10:09:31.576 [main] INFO wasip1test - BAZ: QUX
             * 10:09:31.577 [main] INFO wasip1test - Current time (seconds since EPOCH):
             * 1784275771
             * 10:09:31.577 [main] INFO wasip1test - Random values: [156, 25, 84, 188, 156,
             * 236, 64, 179, 243, 176, 16, 92, 56, 200, 215, 150]
             * 
             */

            String content = bos.toString(StandardCharsets.UTF_8);
            assertTrue(content.contains("Hello, WASI!\n"));
            assertTrue(content.contains("Arguments:\n"));
            assertTrue(content.contains("  Arg 0: wasip1test.wasm\n"));
            assertTrue(content.contains("  Arg 1: hello\n"));
            assertTrue(content.contains("  Arg 2: world\n"));
            assertTrue(content.contains("Environment variables:\n"));
            assertTrue(content.contains("  FOO: BAR\n"));
            assertTrue(content.contains("  BAZ: QUX\n"));
            assertTrue(content.contains("Current time (seconds since EPOCH): "));
            assertTrue(content.contains("Random values: ["));

        }
    }
}
