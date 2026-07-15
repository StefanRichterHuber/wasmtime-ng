package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.io.InputStream;

import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeMemory;

/**
 * WasiFileDecriptor reading all input from a given InputStream
 */
public final class StdinWasiFileDescriptor extends WasiFileDescriptor {
    private final InputStream is;

    /**
     * Creates a new StdinWasiFileDescriptor from a InputStream
     * 
     * @param is InputStream to read from
     */
    public StdinWasiFileDescriptor(InputStream is) {
        super(WasiRights.FD_READ, 0);
        this.is = is;
    }

    @Override
    public int fd_read(WasmtimeMemory memory, int iovs_ptr, int iovs_len, int nread_ptr) {
        return WasiPI1Util.readFromInputStream(is, memory, iovs_ptr, iovs_len, nread_ptr);
    }

    @Override
    public int getType() {
        return WasiFileType.CHARACTER_DEVICE;
    }
}
