package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.io.OutputStream;

import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeMemory;

public final class StdoutWasiFileDescriptor extends WasiFileDescriptor {
    private final OutputStream os;

    public StdoutWasiFileDescriptor(OutputStream os) {
        super(WasiRights.FD_WRITE, 0);
        this.os = os;
    }

    @Override
    public int fd_write(WasmtimeMemory memory, int iovs_ptr, int iovs_len, int nwritten_ptr) {
        return WasiPI1Util.writeToOutputStream(os, memory, iovs_ptr, iovs_len, nwritten_ptr);
    }

    @Override
    public int getType() {
        return WasiFileType.CHARACTER_DEVICE;
    }
}
