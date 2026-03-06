package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.io.IOException;
import java.io.OutputStream;

import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeMemory;

public final class StdoutWasiFileDescriptor extends WasiFileDescriptor {
    private final OutputStream os;

    public StdoutWasiFileDescriptor(OutputStream os) {
        super(WasiRights.FD_WRITE, 0);
        this.os = os;
    }

    @Override
    public int fd_fdstat_get(WasmtimeMemory memory, int ptr) {
        memory.write(ptr, new byte[24]);
        memory.write(ptr, (byte) WasiFileType.CHARACTER_DEVICE);
        memory.writeLong(ptr + 8, rights_base);
        memory.writeLong(ptr + 16, rights_inheriting);
        return WasiErrno.SUCCESS;
    }

    @Override
    public int fd_write(WasmtimeMemory memory, int iovs_ptr, int iovs_len, int nwritten_ptr) {
        int total_written = 0;
        try {
            for (int i = 0; i < iovs_len; i++) {
                int base = iovs_ptr + (i * 8);
                int buf_ptr = memory.readInt(base);
                int buf_len = memory.readInt(base + 4);

                byte[] content = memory.read(buf_ptr, buf_len);
                os.write(content);
                total_written += buf_len;
            }
            os.flush();
            memory.writeInt(nwritten_ptr, total_written);
            return WasiErrno.SUCCESS;
        } catch (IOException e) {
            return WasiErrno.IO;
        }
    }

    @Override
    public int getType() {
        return WasiFileType.CHARACTER_DEVICE;
    }
}
