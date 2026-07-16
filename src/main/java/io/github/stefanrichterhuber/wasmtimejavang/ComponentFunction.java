package io.github.stefanrichterhuber.wasmtimejavang;

/**
 * Functional interface for a Java implementation of a function imported into
 * a WASM component.
 * <br>
 * Unlike {@link WasmtimeFunction} (core wasm, primitives only), arguments and
 * return values here may be any of the plain Java representations of a WIT
 * value: {@link Boolean}, {@link Integer} (bool/s8/u8/s16/u16/s32/u32),
 * {@link Long} (s64/u64), {@link Float}, {@link Double}, {@link Character},
 * {@link String}, {@code byte[]} (list&lt;u8&gt;), {@link java.util.List}
 * (list/map), {@code Object[]} (tuple), {@link java.util.Map} (record),
 * {@link java.util.Optional} (option), {@link java.util.Set} (flags), or the
 * small wrapper types in {@link io.github.stefanrichterhuber.wasmtimejavang.component}
 * ({@code WitVariant}, {@code WitEnum}, {@code WitResult}, {@code WitResource}).
 */
@FunctionalInterface
public interface ComponentFunction {

    /**
     * Executes the Java function.
     *
     * @param instance The WasmtimeComponentInstance of this call.
     * @param args     The arguments passed from the WASM component.
     * @return An array of results to return to the WASM component.
     */
    Object[] call(WasmtimeComponentInstance instance, Object... args);
}
