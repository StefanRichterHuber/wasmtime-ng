package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

public class ProcExitException extends RuntimeException {

    private final int code;

    public ProcExitException(int code) {
        super("WASM program exited with code " + code);
        this.code = code;

    }

    public int getCode() {
        return this.code;
    }

}
