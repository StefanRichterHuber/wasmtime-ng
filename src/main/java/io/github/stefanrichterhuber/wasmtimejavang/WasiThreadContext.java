package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation of wasi-threads.
 * This class provides the thread_spawn function to the WASM module.
 * When called, it spawns a new native thread, creates a fresh instance of the
 * WASM module
 * (sharing the same memory), and calls the wasi_thread_start function in the
 * new instance.
 */
public class WasiThreadContext implements WasmContext {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The main memory of the application
     */
    private static final String STD_MEMORY = "memory";

    private final WasmtimeEngine engine;
    private final WasmtimeModule module;
    private final WasmtimeSharedMemory sharedMemory;

    /**
     * WasmContext to use for threads
     * <br>
     * Thread safety: Read-only after start
     */
    private final List<WasmContext> threadContexts = new ArrayList<>();

    /**
     * Generator for thread IDs.
     */
    private final AtomicInteger nextTid = new AtomicInteger(1);

    /**
     * Creates a new WasiThreadContext.
     * 
     * @param engine          The engine to use for creating new stores and
     *                        linkers.
     * @param module          The module to re-instantiate in new threads.
     * @param sharedMemory    The shared memory to link to new threads.
     * @param initialContexts The initial list of contexts to be linked in new
     *                        threads.
     */
    public WasiThreadContext(WasmtimeEngine engine, WasmtimeModule module, WasmtimeSharedMemory sharedMemory,
            List<WasmContext> initialContexts) {
        this.engine = engine;
        this.module = module;
        this.sharedMemory = sharedMemory;
        if (initialContexts != null) {
            this.threadContexts.addAll(initialContexts);
        }
        // Add itself to geht other threads to start other threads themselves
        this.threadContexts.add(this);
    }

    @Override
    public List<Importmemory> getMemories() {
        return List.of(new Importmemory("env", "memory", sharedMemory));
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
     * @param context  Context Map (shared with the new instance)
     * @param args     Call args (contains the single argument for new thread)
     * @return Thread id
     */
    protected Object[] spawnThread(WasmtimeInstance instance, Map<String, Object> context, Object[] args) {
        final int arg = (int) args[0];
        final int tid = nextTid.getAndIncrement();

        if (!instance.getMemory(STD_MEMORY).isShared()) {
            LOGGER.error("To support wasm multithread, a shared memory must be provided!");
            throw new IllegalStateException("To support wasm multithread, a shared memory must be provided!");
        }

        final Thread thread = new Thread(() -> {
            try (WasmtimeStore threadStore = new WasmtimeStore(engine, context);
                    WasmtimeLinker threadLinker = new WasmtimeLinker(engine, threadStore)) {

                // Link all contexts to the new linker
                for (WasmContext ctx : threadContexts) {
                    threadLinker.link(ctx);
                }

                // Link the shared memory
                threadLinker.defineSharedMemory("env", "memory", sharedMemory);

                try (WasmtimeInstance threadInstance = new WasmtimeInstance(threadStore,
                        module,
                        threadLinker)) {
                    threadInstance.invoke("wasi_thread_start", new Object[] { Long.valueOf(tid), Long.valueOf(arg) });
                } catch (Exception e) {
                    LOGGER.error("Error in wasi-thread start", e);
                }
            } catch (Exception e) {
                LOGGER.error("Error setting up wasi-thread", e);
            }
        });
        thread.start();

        return new Object[] { tid };

    }
}
