package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeMemory;

public final class FileWasiFileDescriptor extends WasiFileDescriptor {
    private final Path path;
    private final FileChannel channel;
    private int fd_flags;

    public FileWasiFileDescriptor(Path path, FileChannel channel, int fd_flags, long rights_base,
            long rights_inheriting) {
        super(rights_base, rights_inheriting);
        this.path = path;
        this.channel = channel;
        this.fd_flags = fd_flags;
    }

    @Override
    public int fd_fdstat_get(WasmtimeMemory memory, int ptr) {
        memory.write(ptr, new byte[24]);
        memory.write(ptr, (byte) WasiFileType.REGULAR_FILE);
        memory.writeShort(ptr + 2, (short) fd_flags);
        memory.writeLong(ptr + 8, rights_base);
        memory.writeLong(ptr + 16, rights_inheriting);
        return WasiErrno.SUCCESS;
    }

    @Override
    public int fd_read(WasmtimeMemory memory, int iovs_ptr, int iovs_len, int nread_ptr) {
        if ((rights_base & WasiRights.FD_READ) == 0)
            return WasiErrno.NOTCAPABLE;
        int total_read = 0;
        try {
            for (int i = 0; i < iovs_len; i++) {
                int base = iovs_ptr + (i * 8);
                int buf_ptr = memory.readInt(base);
                int buf_len = memory.readInt(base + 4);

                ByteBuffer buffer = ByteBuffer.allocate(buf_len);
                int read = channel.read(buffer);
                if (read == -1)
                    break;
                buffer.flip();
                byte[] bytes = new byte[read];
                buffer.get(bytes);
                memory.write(buf_ptr, bytes);
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
    public int fd_write(WasmtimeMemory memory, int iovs_ptr, int iovs_len, int nwritten_ptr) {
        if ((rights_base & WasiRights.FD_WRITE) == 0)
            return WasiErrno.NOTCAPABLE;
        int total_written = 0;
        try {
            for (int i = 0; i < iovs_len; i++) {
                int base = iovs_ptr + (i * 8);
                int buf_ptr = memory.readInt(base);
                int buf_len = memory.readInt(base + 4);

                byte[] content = memory.read(buf_ptr, buf_len);
                ByteBuffer buffer = ByteBuffer.wrap(content);
                int written = channel.write(buffer);
                total_written += written;
            }
            memory.writeInt(nwritten_ptr, total_written);
            return WasiErrno.SUCCESS;
        } catch (IOException e) {
            return WasiErrno.IO;
        }
    }

    @Override
    public int fd_seek(long offset, int whence, int newoffset_ptr, WasmtimeMemory memory) {
        if ((rights_base & WasiRights.FD_SEEK) == 0)
            return WasiErrno.NOTCAPABLE;
        try {
            long new_pos;
            switch (whence) {
                case 0: // SET
                    new_pos = offset;
                    break;
                case 1: // CUR
                    new_pos = channel.position() + offset;
                    break;
                case 2: // END
                    new_pos = channel.size() + offset;
                    break;
                default:
                    return WasiErrno.INVAL;
            }
            channel.position(new_pos);
            memory.writeLong(newoffset_ptr, new_pos);
            return WasiErrno.SUCCESS;
        } catch (IOException e) {
            return WasiErrno.IO;
        }
    }

    @Override
    public int fd_tell(int newoffset_ptr, WasmtimeMemory memory) {
        if ((rights_base & WasiRights.FD_TELL) == 0)
            return WasiErrno.NOTCAPABLE;
        try {
            memory.writeLong(newoffset_ptr, channel.position());
            return WasiErrno.SUCCESS;
        } catch (IOException e) {
            return WasiErrno.IO;
        }
    }

    @Override
    public int fd_sync() {
        if ((rights_base & WasiRights.FD_SYNC) == 0)
            return WasiErrno.NOTCAPABLE;
        try {
            channel.force(true);
            return WasiErrno.SUCCESS;
        } catch (IOException e) {
            return WasiErrno.IO;
        }
    }

    @Override
    public int fd_datasync() {
        if ((rights_base & WasiRights.FD_DATASYNC) == 0)
            return WasiErrno.NOTCAPABLE;
        try {
            channel.force(false);
            return WasiErrno.SUCCESS;
        } catch (IOException e) {
            return WasiErrno.IO;
        }
    }

    @Override
    public int fd_allocate(long offset, long len) {
        if ((rights_base & WasiRights.FD_ALLOCATE) == 0)
            return WasiErrno.NOTCAPABLE;
        try {
            if (offset + len > channel.size()) {
                channel.write(ByteBuffer.wrap(new byte[] { 0 }), offset + len - 1);
            }
            return WasiErrno.SUCCESS;
        } catch (IOException e) {
            return WasiErrno.IO;
        }
    }

    @Override
    public int fd_filestat_set_size(long size) {
        if ((rights_base & WasiRights.FD_FILESTAT_SET_SIZE) == 0)
            return WasiErrno.NOTCAPABLE;
        try {
            channel.truncate(size);
            return WasiErrno.SUCCESS;
        } catch (IOException e) {
            return WasiErrno.IO;
        }
    }

    @Override
    public int fd_filestat_set_times(long atim, long mtim, int fst_flags) {
        if ((rights_base & WasiRights.FD_FILESTAT_SET_TIMES) == 0)
            return WasiErrno.NOTCAPABLE;
        return WasiPI1Util.setFileTimes(path, atim, mtim, fst_flags);
    }

    @Override
    public int fd_fdstat_set_flags(int flags) {
        if ((rights_base & WasiRights.FD_FDSTAT_SET_FLAGS) == 0)
            return WasiErrno.NOTCAPABLE;
        this.fd_flags = flags;
        return WasiErrno.SUCCESS;
    }

    @Override
    public int fd_pread(WasmtimeMemory memory, int iovs_ptr, int iovs_len, long offset, int nread_ptr) {
        if ((rights_base & WasiRights.FD_READ) == 0)
            return WasiErrno.NOTCAPABLE;
        int total_read = 0;
        try {
            long current_offset = offset;
            for (int i = 0; i < iovs_len; i++) {
                int base = iovs_ptr + (i * 8);
                int buf_ptr = memory.readInt(base);
                int buf_len = memory.readInt(base + 4);

                ByteBuffer buffer = ByteBuffer.allocate(buf_len);
                int read = channel.read(buffer, current_offset);
                if (read == -1)
                    break;
                buffer.flip();
                byte[] bytes = new byte[read];
                buffer.get(bytes);
                memory.write(buf_ptr, bytes);
                total_read += read;
                current_offset += read;
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
    public int fd_filestat_get(WasmtimeMemory memory, int ptr) {
        try {
            WasiPI1Util.writeFilestat(memory, ptr, Files.readAttributes(path,
                    java.nio.file.attribute.BasicFileAttributes.class));
            return WasiErrno.SUCCESS;
        } catch (IOException e) {
            return WasiErrno.IO;
        }
    }

    @Override
    public void close() throws Exception {
        channel.close();
    }

    @Override
    public int getType() {
        return WasiFileType.REGULAR_FILE;
    }

    @Override
    public Path getPath() {
        return path;
    }
}
