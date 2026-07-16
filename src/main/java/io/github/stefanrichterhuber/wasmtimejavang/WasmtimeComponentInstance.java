package io.github.stefanrichterhuber.wasmtimejavang;

import java.lang.ref.Cleaner;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an instantiated WebAssembly component.
 * Mirrors {@link WasmtimeInstance}, adapted to the Component Model: exported
 * functions are looked up by interface name + function name and called
 * dynamically with plain Java representations of WIT values.
 */
public final class WasmtimeComponentInstance implements AutoCloseable {

    private final WasmtimeStore store;
    private final WasmtimeComponent component;
    private final WasmtimeComponentLinker linker;

    private long instancePtr;

    private native long createInstance(long componentPtr, long storePtr, long linkerPtr);

    private native static void closeInstance(long instancePtr);

    private native Object[] callComponentFunc(long storePtr, long instancePtr, String interfaceName, String funcName,
            Object[] params);

    private final Cleaner.Cleanable cleanable;

    private static class CleanState implements Runnable {
        private final long instancePtr;

        CleanState(long instancePtr) {
            this.instancePtr = instancePtr;
        }

        @Override
        public void run() {
            WasmtimeComponentInstance.closeInstance(instancePtr);
        }
    }

    /**
     * Creates a new WasmtimeComponentInstance.
     *
     * @param store     The store to use for the instance.
     * @param component The component to instantiate.
     * @param linker    The linker providing imports for the component.
     */
    public WasmtimeComponentInstance(final WasmtimeStore store, final WasmtimeComponent component,
            final WasmtimeComponentLinker linker) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.component = Objects.requireNonNull(component, "component must not be null");
        this.linker = Objects.requireNonNull(linker, "linker must not be null");
        this.instancePtr = createInstance(this.component.getComponentPtr(), this.store.getStorePtr(),
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
     * Invokes an exported component function.
     *
     * @param interfaceName The name of the exporting interface (e.g.
     *                      {@code "wasi:cli/run@0.2.3"}), or the empty string
     *                      for a function exported from the root of the
     *                      component.
     * @param funcName      The name of the exported function.
     * @param args          The arguments to pass to the function.
     * @return An array of result values from the function call.
     */
    public Object[] invoke(String interfaceName, String funcName, Object... args) {
        return callComponentFunc(this.store.getStorePtr(), getInstancePtr(), interfaceName, funcName, args);
    }

    /**
     * Returns the WasmtimeStore of this instance.
     *
     * @return The WasmtimeStore object
     */
    public WasmtimeStore getStore() {
        return this.store;
    }

    /**
     * Returns the shared context object.
     *
     * @return Map with the context
     */
    public Map<String, Object> getContext() {
        return this.getStore().getContext();
    }

    /**
     * Returns the WasmtimeComponent of this instance.
     *
     * @return The WasmtimeComponent object
     */
    public WasmtimeComponent getComponent() {
        return this.component;
    }

    /**
     * Returns the WasmtimeComponentLinker of this instance.
     *
     * @return The WasmtimeComponentLinker object
     */
    public WasmtimeComponentLinker getLinker() {
        return this.linker;
    }
}
