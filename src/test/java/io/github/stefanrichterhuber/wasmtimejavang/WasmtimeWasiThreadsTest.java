package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.wasmtimejavang.wasip1.LoggerOutputStream;

public class WasmtimeWasiThreadsTest {

    /**
     * Wasi-threads proposal is supported
     */
    @Test
    public void testWasiThreads() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
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
                    .withStdOut(bos)
                    .withStdErr(new LoggerOutputStream("WasmtimeWasiThreadsTest", Level.ERROR));

            WasiThreadContext threadContext = new WasiThreadContext();

            linker.linkMemory("env", "memory", sharedMemory).linkContext(wasiContext).linkContext(threadContext);

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                instance.start();
            }

            /*
             * 
             * 10:18:27.629 [pool-2-thread-1] INFO WasmtimeWasiThreadsTest - WASM: Hello
             * from thread ThreadId(2), arg: Greeting from main to thread
             * 10:18:27.635 [pool-2-thread-1] INFO WasmtimeWasiThreadsTest - WASM: Hello
             * from thread ThreadId(3), arg: Greeting from main to thread
             * 10:18:27.640 [pool-2-thread-1] INFO WasmtimeWasiThreadsTest - WASM: Hello
             * from thread ThreadId(4), arg: Greeting from main to thread
             * 
             */

            String content = bos.toString();
            assertTrue(content.contains("Hello from thread ThreadId(2), arg: Greeting from main to thread"));
            assertTrue(content.contains("Hello from thread ThreadId(3), arg: Greeting from main to thread"));
            assertTrue(content.contains("Hello from thread ThreadId(4), arg: Greeting from main to thread"));

        }
    }

    /**
     * One can set a custom executor to execute wasm threads.
     * 
     * @throws Exception
     */
    @Test
    public void testWasiThreadsWithExecutor() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

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
                    .withStdOut(bos)
                    .withStdErr(new LoggerOutputStream("WasmtimeWasiThreadsTest", Level.ERROR));

            WasiThreadContext threadContext = new WasiThreadContext(Executors.newSingleThreadExecutor());

            linker.linkMemory("env", "memory", sharedMemory);
            linker.linkContext(wasiContext);
            linker.linkContext(threadContext);

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                instance.start();
            }
            String content = bos.toString();
            assertTrue(content.contains("Hello from thread ThreadId(2), arg: Greeting from main to thread"));
            assertTrue(content.contains("Hello from thread ThreadId(3), arg: Greeting from main to thread"));
            assertTrue(content.contains("Hello from thread ThreadId(4), arg: Greeting from main to thread"));

        }
    }
}
