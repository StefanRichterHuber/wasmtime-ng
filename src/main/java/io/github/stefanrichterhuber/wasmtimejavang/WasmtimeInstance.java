package io.github.stefanrichterhuber.wasmtimejavang;

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

    private native void closeInstance(long instancePtr);

    private native Object[] runWasmFunc(long storePtr, long instancePtr, String name, Object[] params);

    private native WasmtimeFunction getFunctionReference(long storePtr, long instancePtr, String name);

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
    public Object[] invoke(String name, Object... args) {
        return runWasmFunc(this.store.getStorePtr(), this.instancePtr, name, args);
    }

    /**
     * Invokes the _start (usually does some initializations and then calls main)
     * function of the instance (Present in WASI modules)
     * 
     * @return A list of result values from the function call.
     */
    public Object[] start() {
        return invoke("_start");
    }

    /**
     * Returns a callable reference to any exported wasm function
     * 
     * @param name Name of the exported function
     * @return WasmFunction if present, or null if not
     */
    public WasmtimeFunction getFunction(String name) {
        return getFunctionReference(getStore().getStorePtr(), getInstancePtr(), name);
    }

    /**
     * Returns a handle to an exported memory.
     * 
     * @param name The name of the exported memory.
     * @return A WasmtimeMemory object representing the exported memory.
     */
    public WasmtimeMemory getMemory(String name) {
        return new WasmtimeLocalMemory(this, store, name);
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
     * Returns a the WasmtimeModule of this instance
     * 
     * @return The WasmtimeModule object
     */
    public WasmtimeModule getModule() {
        return this.module;
    }

    /**
     * Returns a the WasmtimeLinker of this instance
     * 
     * @return The WasmtimeLinker object
     */
    public WasmtimeLinker getLinker() {
        return this.linker;
    }
}
