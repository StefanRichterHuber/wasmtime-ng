package io.github.stefanrichterhuber.wasmtimejavang;

/**
 * Represents the value types in WebAssembly.
 */
public enum ValType {
    /** 32-bit integer */
    I32,
    /** 64-bit integer */
    I64,
    /** 32-bit float */
    F32,
    /** 64-bit float */
    F64,
    /** 128-bit vector */
    V128,
    /** Reference type */
    Ref
}
