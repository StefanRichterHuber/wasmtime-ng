package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.Map;

/**
 * Functional interface for a Java implementation of a function imported into /
 * exported from
 * WebAssembly
 */
@FunctionalInterface
public interface WasmtimeFunction {
    /**
     * Executes the Java function.
     * 
     * @param instance WasmtimeInstance of this call, necessary to access the memory
     *                 and call native functions
     * @param context  The context map from the WasmtimeStore.
     * @param args     The arguments passed from WASM. All numeric types are passed
     *                 as Objects (usually Numbers).
     * @return An array of results to return to WASM. All numeric types must be
     *         returned as Objects (usally Numbers).
     */
    Object[] call(WasmtimeInstance instance, Map<String, Object> context, Object... args);
}
