package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.ProcExitException;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.WasiCliContext;

public class WasmtimeWasiP2Test {
    private static final String WASM_PATH = "target/rust-test/wasip2test/wasm32-wasip2/debug/wasip2test.wasm";
    private static final String CLI_WASM_PATH = "target/rust-test/wasip2clitest/wasm32-wasip2/debug/wasip2clitest.wasm";
    private static final String RANDOM_WASM_PATH = "target/rust-test/wasip2randomtest/wasm32-wasip2/debug/wasip2randomtest.wasm";

    @Test
    public void wasip2test() throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        try (
                FileInputStream fis = new FileInputStream(WASM_PATH);
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeComponent component = new WasmtimeComponent(engine, fis);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeComponentLinker linker = new WasmtimeComponentLinker(engine, store)) {

            // Interfaces are read straight off the compiled component via wasmtime,
            // no instantiation or linking required.
            assertTrue(component.isCommand(), "component does not export wasi:cli/run");
            assertTrue(component.getExportInterfaces().stream().anyMatch(n -> n.startsWith("wasi:cli/run@")));
            assertTrue(component.getImportInterfaces().stream().anyMatch(n -> n.startsWith("wasi:io/poll@")));
            assertTrue(component.getImportInterfaces().stream()
                    .anyMatch(n -> n.startsWith("wasi:clocks/monotonic-clock@")));
            assertTrue(component.getImportInterfaces().stream().anyMatch(n -> n.startsWith("wasi:cli/stdout@")));

            // Link a configured "wasi-cli" explicitly to capture stdout; everything
            // else this specific component actually needs ("wasi-io", "wasi-clocks")
            // is discovered from its declared imports and auto-linked via the
            // linker's default ServiceLoaderComponentContextLookup (backed by
            // META-INF/services/...WasmComponentContext) -- nothing this component
            // doesn't use (e.g. wasi:filesystem, wasi:sockets) gets linked.
            linker.linkContext(new WasiCliContext().withStdOut(stdout));
            linker.linkRequired(component);

            try (WasmtimeComponentInstance instance = new WasmtimeComponentInstance(store, component, linker)) {
                WitResult status = instance.asCliRunnable().call();
                assertTrue(status.ok(), "wasi:cli/run#run did not return Ok");
            }
        }

        String output = stdout.toString("UTF-8");
        assertTrue(output.contains("Hello, world!"), "stdout was: " + output);
        assertTrue(output.contains("Napped for"), "stdout was: " + output);
    }

    /**
     * Covers the wasi:cli/wasip2 core surface {@link #wasip2test()} doesn't
     * touch: program arguments, environment variables, stderr, reading from a
     * prepared stdin, terminal detection (always "not a tty"), the wall
     * clock, and a non-zero {@code wasi:cli/exit} exit code.
     */
    @Test
    public void wasip2clitest() throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ByteArrayInputStream stdin = new ByteArrayInputStream("hello from a prepared stdin".getBytes("UTF-8"));

        try (
                FileInputStream fis = new FileInputStream(CLI_WASM_PATH);
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeComponent component = new WasmtimeComponent(engine, fis);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeComponentLinker linker = new WasmtimeComponentLinker(engine, store)) {

            linker.linkContext(new WasiCliContext()
                    .withStdOut(stdout)
                    .withStdErr(stderr)
                    .withStdIn(stdin)
                    .withEnvs(Map.of("FOO", "BAR"))
                    .withArguments(List.of("wasip2clitest", "arg1", "arg2")));
            linker.linkRequired(component);

            try (WasmtimeComponentInstance instance = new WasmtimeComponentInstance(store, component, linker)) {
                // WASI Preview 2's exit(status: result<_, _>) can only convey
                // success/failure, unlike WASI Preview 1's proc_exit(rval: u32) --
                // so a non-zero process::exit(1) in the wasm program surfaces
                // here as exit code 1, not whatever specific code was passed.
                ProcExitException exit = assertThrows(ProcExitException.class,
                        () -> instance.asCliRunnable().call());
                assertEquals(1, exit.getCode());
            }
        }

        String output = stdout.toString("UTF-8");
        assertTrue(output.contains("Hello from wasip2clitest!"), "stdout was: " + output);
        assertTrue(output.contains("ARGS [\"wasip2clitest\", \"arg1\", \"arg2\"]"), "stdout was: " + output);
        assertTrue(output.contains("ENV FOO=BAR"), "stdout was: " + output);
        assertTrue(output.contains("STDIN=hello from a prepared stdin"), "stdout was: " + output);
        assertTrue(output.contains("STDIN_TERMINAL=false"), "stdout was: " + output);
        assertTrue(output.contains("STDOUT_TERMINAL=false"), "stdout was: " + output);
        assertTrue(output.contains("STDERR_TERMINAL=false"), "stdout was: " + output);
        assertTrue(output.contains("WALL_CLOCK_SECONDS="), "stdout was: " + output);

        String errOutput = stderr.toString("UTF-8");
        assertTrue(errOutput.contains("stderr line from wasip2clitest"), "stderr was: " + errOutput);
    }

    @Test
    public void wasip2randomtest() throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

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
    }
}
