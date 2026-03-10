package io.github.stefanrichterhuber.wasmtimejavang;

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
     * Links all import functions from a WasmContext.
     * 
     * @param context The context providing imports (e.g., a WASI context).
     */
    public void link(WasmContext context) {
        for (WasmContext.ImportFunction importFunction : context.getImportFunctions()) {
            this.importFunction(importFunction.module(), importFunction.name(), importFunction.parameters(),
                    importFunction.returnTypes(), importFunction.function());
        }

        for (Importmemory memory : context.getMemories()) {
            this.defineSharedMemory(memory.module(), memory.name(), memory.memory());
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
        defineFunction(this.engine.getEnginePtr(), this.store.getStorePtr(), getLinkerPtr(), f, module, name,
                parameters, returnTypes);
    }

    /**
     * Defines a shared memory in the linker.
     * 
     * @param module The module name for the shared memory import.
     * @param name   The name for the shared memory import.
     * @param memory The shared memory to define.
     */
    public void defineSharedMemory(String module, String name, WasmtimeSharedMemory memory) {
        defineMemory(this.store.getStorePtr(), getLinkerPtr(), memory.getSharedMemoryPtr(), module, name);
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

}
