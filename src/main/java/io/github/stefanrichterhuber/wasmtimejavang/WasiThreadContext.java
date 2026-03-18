package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation of wasi-threads.
 * This class provides the thread_spawn function to the WASM module.
 * When called, uses an Executor to execute the thread, creates a fresh instance
 * of the WASM module (sharing the same memory), and calls the wasi_thread_start
 * function in the new instance.
 */
public class WasiThreadContext implements WasmContext {
    /**
     * The name of the wasi_thread_start function. Called by the new thread.
     */
    private static final String WASI_THREAD_START = "wasi_thread_start";

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The main memory of the application
     */
    private static final String STD_MEMORY = "memory";

    /**
     * Generator for thread IDs.
     */
    private final AtomicInteger nextTid = new AtomicInteger(1);

    /**
     * Executor to run the new threads
     */
    private final Executor threadExecutor;

    /**
     * Creates a new WasiThreadContext.
     */
    public WasiThreadContext() {
        this(Executors.newThreadPerTaskExecutor(Thread::new));
    }

    /**
     * Creates a new WasiThreadContext with a Executor to run new WasmThreads
     * 
     * @param exec Executor to use
     */
    public WasiThreadContext(Executor exec) {
        threadExecutor = exec;
    }

    @Override
    public List<Importmemory> getMemories() {
        return List.of();
    }

    @Override
    public List<ImportFunction> getImportFunctions() {
        List<ImportFunction> result = new ArrayList<>();
        result.add(new ImportFunction("wasi_threads", "thread_spawn",
                List.of(ValType.I32),
                List.of(ValType.I32), this::spawnThread));
        result.add(new ImportFunction("wasi", "thread-spawn",
                List.of(ValType.I32),
                List.of(ValType.I32), this::spawnThread));
        return result;
    }

    /**
     * Creates a new WasmtimeStore, a WasmtimeLinker with a copy of the current
     * contexts and memoryies and a new WasmtimeInstance and starts a new Java
     * thread with this instance.
     * 
     * @param instance Instance requesting the thread
     * @param args     Call args (contains the single argument for new thread)
     * @return Thread id
     */
    protected Object[] spawnThread(WasmtimeInstance instance, Object[] args) {
        final int arg = (int) args[0];
        final int tid = nextTid.getAndIncrement();

        if (!instance.getMemory(STD_MEMORY).isShared()) {
            LOGGER.error("To support wasm multithread, a shared memory must be provided!");
            throw new IllegalStateException("To support wasm multithread, a shared memory must be provided!");
        }

        this.threadExecutor.execute(() -> {
            try (WasmtimeStore threadStore = instance.getStore().createClone();
                    WasmtimeLinker threadLinker = instance.getLinker().createClone(threadStore);
                    WasmtimeInstance threadInstance = new WasmtimeInstance(threadStore, instance.getModule(),
                            threadLinker)) {
                threadInstance.invoke(WASI_THREAD_START, tid, arg);
            } catch (Exception e) {
                LOGGER.error("Error in wasi-thread start", e);
            }
        });
        return new Object[] { tid };

    }

    @Override
    public String name() {
        return "wasi_threads";
    }
}
