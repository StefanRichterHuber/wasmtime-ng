package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.stefanrichterhuber.wasmtimejavang.WasmContext.Importmemory;

/**
 * Used to resolve imports for a WebAssembly module.
 * The linker allows providing Java-implemented functions or WASI contexts
 * to a WASM module during instantiation.
 */
public final class WasmtimeLinker implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger();
    private long linkerPtr;

    private final WasmtimeEngine engine;

    private final WasmtimeStore store;

    private final List<WasmContext> contexts = new ArrayList<>();

    private native long createLinker(long enginePtr);

    private native void closeLinker(long linkerPtr);

    private native void defineFunction(
            long enginePtr, long storePtr, long linkerPtr,
            WasmtimeFunction func,
            String module, String name,
            List<ValType> params, List<ValType> returnTypes);

    private native void defineMemory(
            long storePtr, long linkerPtr, long sharedMemoryPtr,
            String module, String name);

    /**
     * Closes the linker and releases native resources.
     */
    @Override
    public void close() throws Exception {
        if (linkerPtr != 0) {
            this.closeLinker(linkerPtr);
        }
        linkerPtr = 0;
    }

    /**
     * Returns the native pointer to the linker.
     * 
     * @return The native linker pointer.
     */
    long getLinkerPtr() {
        return this.linkerPtr;
    }

    /**
     * Creates a new WasmtimeLinker.
     * 
     * @param engine The engine associated with this linker.
     * @param store  The store associated with this linker.
     */
    public WasmtimeLinker(WasmtimeEngine engine, WasmtimeStore store) {
        if (engine == null) {
            throw new NullPointerException("WasmtimeEngine must not be null");
        }
        if (store == null) {
            throw new NullPointerException("WasmtimeStore must not be null");
        }
        this.engine = engine;
        this.store = store;
        this.linkerPtr = createLinker(engine.getEnginePtr());
    }

    /**
     * Links all import functions from a collection of WasmContexts.
     * 
     * @param contexts The collection of contexts providing imports (e.g., WASI
     *                 contexts).
     */
    public void link(Iterable<? extends WasmContext> contexts) {
        for (WasmContext context : contexts) {
            this.link(context);
        }
    }

    /**
     * Links all import functions from a WasmContext.
     * 
     * @param context The context providing imports (e.g., a WASI context).
     */
    public void link(WasmContext context) {
        this.contexts.add(context);
        LOGGER.debug("Adding context {} to store", context.name());
        for (WasmContext.ImportFunction importFunction : context.getImportFunctions()) {
            this.defineFunction(getEngine().getEnginePtr(), getStore().getStorePtr(), getLinkerPtr(),
                    importFunction.function(), importFunction.module(), importFunction.name(),
                    importFunction.parameters(), importFunction.returnTypes());
        }

        for (Importmemory memory : context.getMemories()) {
            this.defineMemory(getStore().getStorePtr(), getLinkerPtr(), memory.memory().getSharedMemoryPtr(),
                    memory.module(), memory.name());
        }
    }

    /**
     * Explicitly imports a Java function into the WASM module.
     * 
     * @param module      The name of the module providing the import.
     * @param name        The name of the imported function.
     * @param parameters  The parameter types of the function.
     * @param returnTypes The return types of the function.
     * @param f           The Java function implementation.
     */
    public void importFunction(String module, String name, List<ValType> parameters, List<ValType> returnTypes,
            WasmtimeFunction f) {

        this.link(new WasmContext() {
            @Override
            public List<ImportFunction> getImportFunctions() {
                return List.of(new ImportFunction(module, name, parameters, returnTypes, f));
            }

            @Override
            public List<Importmemory> getMemories() {
                return List.of();
            }

            @Override
            public String name() {
                return String.format("function %s::%s(%s) -> %s", module, name, parameters, returnTypes);
            }
        });
    }

    /**
     * Defines a shared memory in the linker.
     * 
     * @param module The module name for the shared memory import.
     * @param name   The name for the shared memory import.
     * @param memory The shared memory to define.
     */
    public void defineSharedMemory(String module, String name, WasmtimeSharedMemory memory) {
        this.link(new WasmContext() {
            @Override
            public List<ImportFunction> getImportFunctions() {
                return List.of();
            }

            @Override
            public List<Importmemory> getMemories() {
                return List.of(new Importmemory(module, name, memory));
            }

            @Override
            public String name() {
                return String.format("shared memory %s::%s", module, name);
            }
        });
    }

    /**
     * Returns a the WasmtimeStore of this instance
     * 
     * @return The WasmtimeStore object
     */
    public WasmtimeStore getStore() {
        return this.store;
    }

    /**
     * Returns a the WasmtimeEngine of this instance
     * 
     * @return The WasmtimeEngine object
     */
    public WasmtimeEngine getEngine() {
        return this.engine;
    }

    /**
     * Returns a copy of the list of contexts that have been linked. This includes
     * all directly defined memory and function imports.
     * 
     * @return A copy of the list of contexts.
     */
    public List<WasmContext> getContexts() {
        return new ArrayList<>(this.contexts);
    }

    /**
     * Creates a clone of this linker with the same contexts, functions and shared
     * memories.
     * 
     * @param store The store to use for the new linker.
     * @return A new WasmtimeLinker instance with the same contexts, functions and
     *         shared memories.
     */
    public WasmtimeLinker createClone(WasmtimeStore store) {
        WasmtimeLinker result = new WasmtimeLinker(this.engine, store);
        result.link(this.contexts);
        return result;
    }
}
