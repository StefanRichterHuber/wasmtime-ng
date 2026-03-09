package io.github.stefanrichterhuber.wasmtimejavang.internal;

import java.util.Map;

import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeFunction;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeInstance;

final class WasmtimeFuncRef implements WasmtimeFunction {
    private final long funcPtr;
    private final long storePtr;

    private native Object[] invokeNativeFunc(long funcPtr, long storePtr, Object instance,
            Map<String, Object> context,
            Object[] args);

    /**
     * This object is only instantiated from the native context and never from
     * Java runtime
     * 
     * @param funcPtr  The pointer to the function
     * @param storePtr The pointer to the store
     */
    private WasmtimeFuncRef(long funcPtr, long storePtr) {
        this.funcPtr = funcPtr;
        this.storePtr = storePtr;
    }

    @Override
    public Object[] call(WasmtimeInstance instance, Map<String, Object> context, Object... args) {
        return invokeNativeFunc(funcPtr, storePtr, instance, context, args);
    }

}
