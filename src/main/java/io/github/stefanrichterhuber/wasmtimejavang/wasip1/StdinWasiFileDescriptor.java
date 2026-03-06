package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.io.IOException;
import java.io.InputStream;

import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeMemory;

public final class StdinWasiFileDescriptor extends WasiFileDescriptor {
    private final InputStream is;

    public StdinWasiFileDescriptor(InputStream is) {
        super(WasiRights.FD_READ, 0);
        this.is = is;
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
    public int fd_read(WasmtimeMemory memory, int iovs_ptr, int iovs_len, int nread_ptr) {
        int total_read = 0;
        try {
            for (int i = 0; i < iovs_len; i++) {
                int base = iovs_ptr + (i * 8);
                int buf_ptr = memory.readInt(base);
                int buf_len = memory.readInt(base + 4);

                byte[] buffer = new byte[buf_len];
                int read = is.read(buffer);
                if (read == -1)
                    break;
                memory.write(buf_ptr, java.util.Arrays.copyOf(buffer, read));
                total_read += read;
                if (read < buf_len)
                    break;
            }
            memory.writeInt(nread_ptr, total_read);
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
