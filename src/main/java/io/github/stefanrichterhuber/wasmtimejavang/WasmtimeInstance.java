package io.github.stefanrichterhuber.wasmtimejavang;

import java.lang.ref.Cleaner;
import java.util.Map;
import java.util.Objects;

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

    private native static void closeInstance(long instancePtr);

    private native Object[] runWasmFunc(long storePtr, long instancePtr, String name, Object[] params);

    private native WasmtimeFunction getFunctionReference(long storePtr, long instancePtr, String name);

    private final Cleaner.Cleanable cleanable;

    private static class CleanState implements Runnable {
        private final long instancePtr;

        CleanState(long instancePtr) {
            this.instancePtr = instancePtr;
        }

        @Override
        public void run() {
            WasmtimeInstance.closeInstance(instancePtr);
        }
    }

    /**
     * Creates a new WasmtimeInstance.
     * 
     * @param store  The store to use for the instance.
     * @param module The module to instantiate.
     * @param linker The linker providing imports for the module.
     */
    public WasmtimeInstance(final WasmtimeStore store, final WasmtimeModule module, final WasmtimeLinker linker) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.module = Objects.requireNonNull(module, "module must not be null");
        this.linker = Objects.requireNonNull(linker, "linker must not be null");
        this.instancePtr = createInstance(this.module.getModulePtr(), this.store.getStorePtr(),
                this.linker.getLinkerPtr());
        this.cleanable = WasmtimeEngine.CLEANER.register(this, new CleanState(this.instancePtr));
    }

    /**
     * Closes the instance and releases native resources.
     */
    @Override
    public void close() throws Exception {
        if (instancePtr != 0) {
            cleanable.clean();
        }
        instancePtr = 0;
    }

    /**
     * Returns the native pointer to the instance.
     * 
     * @return The native instance pointer.
     */
    long getInstancePtr() {
        if (instancePtr == 0) {
            throw new IllegalStateException("Instance no longer active");
        }
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
        return runWasmFunc(this.store.getStorePtr(), getInstancePtr(), name, args);
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
        return new WasmtimeLocalMemory(this, getStore(), name);
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
     * Returns the shared context object
     * 
     * @return Map with the context
     */
    public Map<String, Object> getContext() {
        return this.getStore().getContext();
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
