package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

public class WasmtimeWasiThreadsTest {

    @Test
    public void testWasiThreads() throws Exception {
        // Path should be where Maven build puts it
        Path wasmPath = Paths.get("target/rust-test/wasip1threadtest/wasm32-wasip1-threads/debug/libwasip1threadtest.wasm");
        if (!Files.exists(wasmPath)) {
            // Fallback for manual run if Maven wasn't used
            wasmPath = Paths.get("src/test/rust/wasip1threadtest/target/wasm32-wasip1-threads/debug/libwasip1threadtest.wasm");
        }
        
        if (!Files.exists(wasmPath)) {
            // Try to build it if it doesn't exist
            System.out.println("Wasm binary not found, building it...");
            ProcessBuilder pb = new ProcessBuilder("cargo", "build", "--target", "wasm32-wasip1-threads");
            pb.directory(new File("src/test/rust/wasip1threadtest"));
            pb.inheritIO();
            pb.start().waitFor();
        }
        assertTrue(Files.exists(wasmPath), "Wasm file should exist at " + wasmPath.toAbsolutePath());

        byte[] wasmBytes = Files.readAllBytes(wasmPath);

        try (WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, wasmBytes);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store)) {

            WasiPI1Context wasiContext = new WasiPI1Context()
                    .withStdOut(System.out)
                    .withStdErr(System.err);
            
            WasiThreadContext threadContext = new WasiThreadContext(engine, module, List.of(wasiContext));
            // Add itself to the list of contexts to be linked (for recursive threads)
            threadContext.addContext(threadContext);

            linker.link(wasiContext);
            linker.link(threadContext);

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                instance.invoke("test_entry", List.of());
            }

            // Give some time for threads to finish and print
            Thread.sleep(1000);
        }
    }
}
