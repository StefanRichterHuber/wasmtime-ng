package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeMemory;

/**
 * A WASI file descriptor representing a network socket.
 */
public class SocketWasiFileDescriptor extends WasiFileDescriptor {
    private final Socket socket;
    private final InputStream is;
    private final OutputStream os;

    public SocketWasiFileDescriptor(Socket socket, long rights_base, long rights_inheriting) throws Exception {
        super(rights_base, rights_inheriting);
        this.socket = socket;
        this.is = socket.getInputStream();
        this.os = socket.getOutputStream();
    }

    @Override
    public int fd_fdstat_get(WasmtimeMemory memory, int ptr) {
        memory.write(ptr, (byte) getType()); // fs_filetype
        memory.writeShort(ptr + 2, (short) 0); // fs_flags
        memory.writeLong(ptr + 8, rights_base); // fs_rights_base
        memory.writeLong(ptr + 16, rights_inheriting); // fs_rights_inheriting
        return WasiErrno.SUCCESS;
    }

    @Override
    public int fd_read(WasmtimeMemory memory, int iovs_ptr, int iovs_len, int nread_ptr) {
        return WasiPI1Util.readFromInputStream(is, memory, iovs_ptr, iovs_len, nread_ptr);
    }

    @Override
    public int fd_write(WasmtimeMemory memory, int iovs_ptr, int iovs_len, int nwritten_ptr) {
        return WasiPI1Util.writeToOutputStream(os, memory, iovs_ptr, iovs_len, nwritten_ptr);
    }

    @Override
    public int sock_recv(WasmtimeMemory memory, int ri_data_ptr, int ri_data_len, int ri_flags, int ro_datalen_ptr,
            int ro_flags_ptr) {
        // Simple implementation delegating to fd_read for now
        int result = fd_read(memory, ri_data_ptr, ri_data_len, ro_datalen_ptr);
        if (result == WasiErrno.SUCCESS) {
            memory.writeInt(ro_flags_ptr, 0); // No flags
        }
        return result;
    }

    @Override
    public int sock_send(WasmtimeMemory memory, int si_data_ptr, int si_data_len, int si_flags, int so_datalen_ptr) {
        // Simple implementation delegating to fd_write
        return fd_write(memory, si_data_ptr, si_data_len, so_datalen_ptr);
    }

    @Override
    public int sock_shutdown(int how) {
        try {
            switch (how) {
                case 1: // SHUT_RD
                    socket.shutdownInput();
                    break;
                case 2: // SHUT_WR
                    socket.shutdownOutput();
                    break;
                case 3: // SHUT_RDWR
                    socket.shutdownInput();
                    socket.shutdownOutput();
                    break;
                default:
                    return WasiErrno.INVAL;
            }
            return WasiErrno.SUCCESS;
        } catch (Exception e) {
            return WasiErrno.IO;
        }
    }

    @Override
    public void close() throws Exception {
        socket.close();
    }

    @Override
    public int getType() {
        return WasiFileType.SOCKET_STREAM;
    }
}
