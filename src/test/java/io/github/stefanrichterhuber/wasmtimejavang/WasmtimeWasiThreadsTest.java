package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

public class WasmtimeWasiThreadsTest {

    @Test
    public void testWasiThreads() throws Exception {
        // Path should be where Maven build puts it
        Path wasmPath = Paths
                .get("target/rust-test/wasip1threadtest/wasm32-wasip1-threads/debug/wasip1threadtest.wasm");

        try (FileInputStream fis = new FileInputStream(wasmPath.toFile());
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, fis);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);
                // A shared memory is necessary to share memory between threads
                WasmtimeSharedMemory sharedMemory = new WasmtimeSharedMemory(engine, 2, 256)) {

            WasiPI1Context wasiContext = new WasiPI1Context()
                    .withArguments(List.of("wasip1threadtest"))
                    .withStdOut(System.out)
                    .withStdErr(System.err);

            WasiThreadContext threadContext = new WasiThreadContext();

            linker.defineSharedMemory("env", "memory", sharedMemory);
            linker.link(wasiContext);
            linker.link(threadContext);

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                instance.start();
            }

            // Give some time for threads to finish and print
            Thread.sleep(1000);
        }
    }
}
