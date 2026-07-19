package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.Objects;
import java.util.function.Function;

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
     * @param args     The arguments passed from WASM. All numeric types are passed
     *                 as Objects (usually Numbers).
     * @return An array of results to return to WASM. All numeric types must be
     *         returned as Objects (usally Numbers).
     */
    Object[] call(WasmtimeInstance instance, Object... args);

    /**
     * Returns a composed function that first applies this function to its input,
     * and then applies the after function to the result. If evaluation of either
     * function throws an exception, it is relayed to the caller of the composed
     * function.
     * 
     * @param after Function to apply on the result of this function
     * @return New WasmtimeFunction
     */
    default WasmtimeFunction andThen(Function<Object[], Object[]> after) {
        Objects.requireNonNull(after);
        return (i, a) -> after.apply(this.call(i, a));
    }

    /**
     * Creates a Function from this WasmtimeFunction by binding the instance
     * <br>
     * Warning: Ensure that the instance remains active for the whole lifetime of
     * the function!
     * 
     * @param instance WasmtimeInstance to bind
     * @return Function created
     */
    default Function<Object[], Object[]> bind(WasmtimeInstance instance) {
        Objects.requireNonNull(instance);
        return (args) -> this.call(instance, args);
    }
}
