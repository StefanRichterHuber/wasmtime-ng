package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.nio.file.Path;

import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeMemory;

/**
 * Base class for all WASI file descriptors.
 * A file descriptor represents an open file, directory, or stream in the WASI
 * environment.
 */
public abstract class WasiFileDescriptor implements AutoCloseable {
    protected long rights_base;
    protected long rights_inheriting;

    /**
     * Creates a new WasiFileDescriptor.
     * 
     * @param rights_base       The base rights associated with this descriptor.
     * @param rights_inheriting The rights that are inherited by descriptors
     *                          created from this one.
     */
    public WasiFileDescriptor(long rights_base, long rights_inheriting) {
        this.rights_base = rights_base;
        this.rights_inheriting = rights_inheriting;
    }

    /**
     * Implementation of fd_advise.
     */
    public int fd_advise(long offset, long len, int advice) {
        return WasiErrno.SUCCESS;
    }

    /**
     * Implementation of fd_allocate.
     */
    public int fd_allocate(long offset, long len) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of fd_datasync.
     */
    public int fd_datasync() {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of fd_fdstat_get.
     * Writes the fdstat structure to the specified memory pointer.
     */
    public abstract int fd_fdstat_get(WasmtimeMemory memory, int ptr);

    /**
     * Implementation of fd_fdstat_set_flags.
     */
    public int fd_fdstat_set_flags(int flags) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of fd_fdstat_set_rights.
     */
    public int fd_fdstat_set_rights(long rights_base, long rights_inheriting) {
        if ((rights_base & ~this.rights_base) != 0 || (rights_inheriting & ~this.rights_inheriting) != 0) {
            return WasiErrno.NOTCAPABLE;
        }
        this.rights_base = rights_base;
        this.rights_inheriting = rights_inheriting;
        return WasiErrno.SUCCESS;
    }

    /**
     * Implementation of fd_filestat_get.
     * Writes the filestat structure to the specified memory pointer.
     */
    public int fd_filestat_get(WasmtimeMemory memory, int ptr) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of fd_filestat_set_size.
     */
    public int fd_filestat_set_size(long size) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of fd_filestat_set_times.
     */
    public int fd_filestat_set_times(long atim, long mtim, int fst_flags) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of fd_pread.
     */
    public int fd_pread(WasmtimeMemory memory, int iovs_ptr, int iovs_len, long offset, int nread_ptr) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of fd_pwrite.
     */
    public int fd_pwrite(WasmtimeMemory memory, int iovs_ptr, int iovs_len, long offset, int nwritten_ptr) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of fd_read.
     */
    public int fd_read(WasmtimeMemory memory, int iovs_ptr, int iovs_len, int nread_ptr) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of fd_readdir.
     */
    public int fd_readdir(WasmtimeMemory memory, int buf_ptr, int buf_len, long cookie, int nwritten_ptr) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of fd_seek.
     */
    public int fd_seek(long offset, int whence, int newoffset_ptr, WasmtimeMemory memory) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of fd_sync.
     */
    public int fd_sync() {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of fd_tell.
     */
    public int fd_tell(int newoffset_ptr, WasmtimeMemory memory) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of fd_write.
     */
    public int fd_write(WasmtimeMemory memory, int iovs_ptr, int iovs_len, int nwritten_ptr) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of sock_recv.
     */
    public int sock_recv(WasmtimeMemory memory, int ri_data_ptr, int ri_data_len, int ri_flags, int ro_datalen_ptr,
            int ro_flags_ptr) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of sock_send.
     */
    public int sock_send(WasmtimeMemory memory, int si_data_ptr, int si_data_len, int si_flags, int so_datalen_ptr) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of sock_shutdown.
     */
    public int sock_shutdown(int how) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Implementation of sock_accept.
     */
    public int sock_accept(int flags, int fd_ptr, WasmtimeMemory memory) {
        return WasiErrno.NOTCAPABLE;
    }

    /**
     * Closes the descriptor and releases host resources.
     */
    @Override
    public void close() throws Exception {
    }

    /**
     * Returns the WASI file type of this descriptor.
     * 
     * @return The WASI file type.
     */
    public abstract int getType();

    /**
     * Returns the host path associated with this descriptor, if any.
     * 
     * @return The Path, or null if not applicable.
     */
    public Path getPath() {
        return null;
    }
}
