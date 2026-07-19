package io.github.stefanrichterhuber.wasmtimejavang.internal;

import java.lang.ref.Cleaner;

import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeFunction;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeInstance;

final class WasmtimeFuncRef implements WasmtimeFunction, AutoCloseable {
    private long funcPtr;
    private final long storePtr;
    // Keeping a reference to the WasmtimeInstance prevents it from silently GCed
    // while this funcref is still referenced
    private final WasmtimeInstance instance;

    private native Object[] invokeNativeFunc(long funcPtr, long storePtr, WasmtimeInstance instance,
            Object[] args);

    private static native void closeNativeFunc(long funcPtr);

    private static final Cleaner CLEANER = Cleaner.create();
    private final Cleaner.Cleanable cleanable;

    private static class CleanState implements Runnable {
        private final long funcPtr;

        CleanState(long funcPtr) {
            this.funcPtr = funcPtr;
        }

        @Override
        public void run() {
            WasmtimeFuncRef.closeNativeFunc(funcPtr);
        }

    }

    /**
     * This object is only instantiated from the native context and never from
     * Java runtime
     * 
     * @param funcPtr  The pointer to the function
     * @param storePtr The pointer to the store
     */
    private WasmtimeFuncRef(long funcPtr, long storePtr, WasmtimeInstance instance) {
        this.funcPtr = funcPtr;
        this.storePtr = storePtr;
        this.cleanable = CLEANER.register(this, new CleanState(this.funcPtr));
        this.instance = instance;
    }

    @Override
    public Object[] call(WasmtimeInstance instance, Object... args) {
        if (this.funcPtr != 0) {
            return invokeNativeFunc(funcPtr, storePtr, instance, args);
        } else {
            throw new IllegalStateException("Function Reference already closed!");
        }
    }

    @Override
    public void close() throws Exception {
        if (funcPtr != 0) {
            this.cleanable.clean();
            funcPtr = 0;
        }
    }

}
