package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.WasiCliContext;

public class WasmtimeWasiRandomTest {
    private static final String RANDOM_WASM_PATH = "target/rust-test/wasip2randomtest/wasm32-wasip2/debug/wasip2randomtest.wasm";

    private static final Logger LOGGER = LogManager.getLogger();

    @Test
    public void wasip2randomtest() throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        LOGGER.info("Started wasip2randomtest");
        try (
                FileInputStream fis = new FileInputStream(RANDOM_WASM_PATH);
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeComponent component = new WasmtimeComponent(engine, fis);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeComponentLinker linker = new WasmtimeComponentLinker(engine, store)) {

            assertTrue(component.isCommand(), "component does not export wasi:cli/run");
            // Link WasiCliContext so we can capture stdout of the println calls in our rust
            // code
            linker.linkContext(new WasiCliContext().withStdOut(stdout));

            // WasiRandomContext will be auto-linked because of the ServiceLoader mechanism
            linker.linkRequired(component);

            try (WasmtimeComponentInstance instance = new WasmtimeComponentInstance(store, component, linker)) {
                WitResult status = instance.asCliRunnable().call();
                assertTrue(status.ok(), "wasi:cli/run#run did not return Ok");
            }
        }

        String output = stdout.toString("UTF-8");
        assertTrue(output.contains("RANDOM BYTES="), "stdout was: " + output);
        assertTrue(output.contains("HASH MAP SIZE=2"), "stdout was: " + output);
        LOGGER.info("Stopped wasip2randomtest");
    }
}
