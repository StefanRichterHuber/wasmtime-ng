package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.List;

/**
 * Represents an instantiated WebAssembly module.
 * An instance contains the state of the WASM module and allows calling its
 * exported functions.
 */
public final class WasmtimeInstance implements AutoCloseable {

    private final WasmtimeStore store;
    private final WasmtimeModule module;
    private final WasmtimeLinker linker;

    private long instancePtr;

    private native long createInstance(long modulePtr, long storePtr, long linkerPtr);

    private native void closeInstance(long linkerPtr);

    private native List<Object> runWasmFunc(long storePtr, long instancePtr, String name, List<Object> params);

    /**
     * Creates a new WasmtimeInstance.
     * 
     * @param store  The store to use for the instance.
     * @param module The module to instantiate.
     * @param linker The linker providing imports for the module.
     */
    public WasmtimeInstance(final WasmtimeStore store, final WasmtimeModule module, final WasmtimeLinker linker) {
        this.store = store;
        this.module = module;
        this.linker = linker;
        this.instancePtr = createInstance(this.module.getModulePtr(), this.store.getStorePtr(),
                this.linker.getLinkerPtr());
    }

    /**
     * Closes the instance and releases native resources.
     */
    @Override
    public void close() throws Exception {
        if (instancePtr != 0) {
            this.closeInstance(instancePtr);
        }
        instancePtr = 0;
    }

    /**
     * Returns the native pointer to the instance.
     * 
     * @return The native instance pointer.
     */
    long getInstancePtr() {
        return this.instancePtr;
    }

    /**
     * Invokes an exported WebAssembly function.
     * 
     * @param name The name of the exported function.
     * @param args The arguments to pass to the function.
     * @return A list of result values from the function call.
     */
    public List<Object> invoke(String name, List<Object> args) {
        return runWasmFunc(this.store.getStorePtr(), this.instancePtr, name, args);
    }

    /**
     * Returns a handle to an exported memory.
     * 
     * @param name The name of the exported memory.
     * @return A WasmtimeMemory object representing the exported memory.
     */
    public WasmtimeMemory getMemory(String name) {
        return new WasmtimeMemory(this, store, name);
    }
}
