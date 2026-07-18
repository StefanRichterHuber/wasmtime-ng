package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Function;

import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeMemory;

/**
 * A WASI file descriptor representing a server-side listening socket.
 */
public class ServerSocketWasiFileDescriptor extends WasiFileDescriptor {
    private final ServerSocket serverSocket;
    private final Function<Socket, WasiFileDescriptor> socketToFd;

    public ServerSocketWasiFileDescriptor(ServerSocket serverSocket, long rights_base, long rights_inheriting,
            Function<Socket, WasiFileDescriptor> socketToFd) {
        super(rights_base, rights_inheriting);
        this.serverSocket = serverSocket;
        this.socketToFd = socketToFd;
    }

    @Override
    public int sock_accept(int flags, int fd_ptr, WasmtimeMemory memory) {
        try {
            Socket client = serverSocket.accept();
            @SuppressWarnings("unused")
            WasiFileDescriptor clientFd = socketToFd.apply(client);
            // This is a bit tricky as the context needs to assign the FD number.
            // We'll return the WasiFileDescriptor object and let the context handle the
            // mapping.
            // But WASI expects us to write the FD number into fd_ptr.
            // We'll need to change how we handle sock_accept in the context to accommodate
            // this.
            throw new UnsupportedOperationException("sock_accept must be coordinated with WasiPI1Context");
        } catch (Exception e) {
            return WasiErrno.IO;
        }
    }

    /**
     * Accepts a connection
     * 
     * @return Socket client connection
     * @throws Exception
     */
    public Socket accept() throws Exception {
        return serverSocket.accept();
    }

    @Override
    public void close() throws Exception {
        serverSocket.close();
    }

    @Override
    public int getType() {
        return WasiFileType.SOCKET_STREAM;
    }
}
