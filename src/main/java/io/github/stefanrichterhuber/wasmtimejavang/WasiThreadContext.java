package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
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
     * Wasm module for thread support
     */
    private static final String WASI_THREADS_MODULE = "wasi_threads";

    private final WasmtimeEngine engine;
    private final WasmtimeModule module;
    private final List<WasmContext> contexts = new CopyOnWriteArrayList<>();

    /**
     * Generator for thread IDs.
     */
    private final AtomicInteger nextTid = new AtomicInteger(1);

    /**
     * Creates a new WasiThreadContext.
     * 
     * @param engine           The engine to use for creating new stores and
     *                         linkers.
     * @param module           The module to re-instantiate in new threads.
     * @param initialContexts  The initial list of contexts to be linked in new
     *                         threads.
     */
    public WasiThreadContext(WasmtimeEngine engine, WasmtimeModule module, List<WasmContext> initialContexts) {
        this.engine = engine;
        this.module = module;
        if (initialContexts != null) {
            this.contexts.addAll(initialContexts);
        }
    }

    /**
     * Adds a context to be linked in new threads.
     * 
     * @param context The context to add.
     */
    public void addContext(WasmContext context) {
        this.contexts.add(context);
    }

    @Override
    public List<ImportFunction> getImportFunctions() {
        List<ImportFunction> result = new ArrayList<>();
        result.add(new ImportFunction(WASI_THREADS_MODULE, "thread_spawn",
                List.of(ValType.I32),
                List.of(ValType.I32), (WasmtimeInstance instance, Map<String, Object> context, long[] args) -> {
                    final int arg = (int) args[0];
                    final int tid = nextTid.getAndIncrement();

                    final Thread thread = new Thread(() -> {
                        try (WasmtimeStore threadStore = new WasmtimeStore(engine);
                                WasmtimeLinker threadLinker = new WasmtimeLinker(engine, threadStore)) {
                            // Link all contexts to the new linker
                            for (WasmContext ctx : contexts) {
                                threadLinker.link(ctx);
                            }

                            try (WasmtimeInstance threadInstance = new WasmtimeInstance(threadStore,
                                    module,
                                    threadLinker)) {
                                threadInstance.invoke("wasi_thread_start", List.of((long) tid, (long) arg));
                            } catch (Exception e) {
                                LOGGER.error("Error in wasi-thread start", e);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Error setting up wasi-thread", e);
                        }
                    });
                    thread.start();

                    return new long[] { tid };
                }));

        return result;
    }

}
