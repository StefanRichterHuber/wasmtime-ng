package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasicli.WasiCliContext;

/**
 * End-to-end example of a component requiring a custom, non-WASI interface
 * ({@code my:custom/greet@1.0.0}, declared in
 * {@code src/test/rust/wasip2customtest/wit/world.wit} and consumed via
 * {@code wit_bindgen::generate!} in that fixture's {@code main.rs}) provided
 * entirely from Java via {@link GreetComponentContext}. Everything else the
 * component needs ({@code wasi:cli}, {@code wasi:io}) is wired up the normal
 * way: {@code wasi-cli} linked explicitly to capture stdout,
 * {@code wasi-io} auto-linked by {@link WasmtimeComponentLinker#linkRequired}.
 */
public class WasmtimeCustomComponentTest {
    private static final String WASM_PATH = "target/rust-test/wasip2customtest/wasm32-wasip2/debug/wasip2customtest.wasm";

    @Test
    public void wasip2customtest() throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        try (
                FileInputStream fis = new FileInputStream(WASM_PATH);
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeComponent component = new WasmtimeComponent(engine, fis);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeComponentLinker linker = new WasmtimeComponentLinker(engine, store)) {

            assertTrue(component.getImportInterfaces().stream().anyMatch(n -> n.equals("my:custom/greet@1.0.0")),
                    "component does not import my:custom/greet@1.0.0");

            linker.linkContext(new WasiCliContext().withStdOut(stdout));
            linker.linkContext(new GreetComponentContext());
            linker.linkRequired(component); // pulls in wasi-io for stdout

            try (WasmtimeComponentInstance instance = new WasmtimeComponentInstance(store, component, linker)) {
                WitResult status = instance.asCliRunnable().call();
                assertTrue(status.ok(), "wasi:cli/run#run did not return Ok");
            }
        }

        String output = stdout.toString("UTF-8");
        assertTrue(output.contains("GREETING=Hello, Wasmtime-Java!"), "stdout was: " + output);
        assertTrue(output.contains("SUM=42"), "stdout was: " + output);
        assertTrue(output.contains("Done!"), "stdout was: " + output);
    }
}
