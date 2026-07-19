package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import io.github.stefanrichterhuber.wasmtimejavang.WasmRuntimeException;

/**
 * Exception thrown when a wasm ends with a exit code
 */
public class ProcExitException extends WasmRuntimeException {

    /**
     * Exit code
     */
    private final int code;

    /**
     * Creates a new ProcExitException wrapping the given exit code
     * 
     * @param code Exit code
     */
    public ProcExitException(int code) {
        super("WASM program exited with code " + code);
        this.code = code;

    }

    /**
     * Exit code provided
     * 
     * @return Exit code
     */
    public int getCode() {
        return this.code;
    }

}
