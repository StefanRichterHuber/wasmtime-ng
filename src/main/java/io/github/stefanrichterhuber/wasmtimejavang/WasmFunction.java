package io.github.stefanrichterhuber.wasmtimejavang;

public interface WasmFunction {
    long[] call(long[] context);
}
