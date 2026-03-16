package io.github.stefanrichterhuber.wasmtimejavang;

public class WasmRuntimeException extends RuntimeException {
    private String wasmStack;

    public WasmRuntimeException(String message) {
        super(message);
    }

    public WasmRuntimeException(String message, String wasmStack) {
        super(message);
        this.wasmStack = wasmStack;
    }

    public WasmRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public WasmRuntimeException(String message, String wasmStack, Throwable cause) {
        super(message, cause);
        this.wasmStack = wasmStack;
    }

    public String getWasmStack() {
        return wasmStack;
    }

    /**
     * Only called from native wasm context
     * 
     * @param wasmStack
     */
    @SuppressWarnings("unused")
    private void setWasmStack(String wasmStack) {
        this.wasmStack = wasmStack;
    }

    public String getMessage() {
        return super.getMessage() + "\n" + getWasmStack();
    }
}
