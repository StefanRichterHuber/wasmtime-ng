package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import io.github.stefanrichterhuber.wasmtimejavang.WasmRuntimeException;

public class ProcExitException extends WasmRuntimeException {

    private final int code;

    public ProcExitException(int code) {
        super("WASM program exited with code " + code);
        this.code = code;

    }

    public int getCode() {
        return this.code;
    }

}
