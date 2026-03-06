package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeMemory;

public final class DirectoryWasiFileDescriptor extends WasiFileDescriptor {
    private final Path path;

    public DirectoryWasiFileDescriptor(Path path, long rights_base, long rights_inheriting) {
        super(rights_base, rights_inheriting);
        this.path = path;
    }

    @Override
    public int fd_fdstat_get(WasmtimeMemory memory, int ptr) {
        memory.write(ptr, new byte[24]);
        memory.write(ptr, (byte) WasiFileType.DIRECTORY);
        memory.writeLong(ptr + 8, rights_base);
        memory.writeLong(ptr + 16, rights_inheriting);
        return WasiErrno.SUCCESS;
    }

    @Override
    public int fd_filestat_get(WasmtimeMemory memory, int ptr) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            WasiPI1Util.writeFilestat(memory, ptr, attrs);
            return WasiErrno.SUCCESS;
        } catch (IOException e) {
            return WasiErrno.IO;
        }
    }

    @Override
    public int fd_readdir(WasmtimeMemory memory, int buf_ptr, int buf_len, long cookie, int nwritten_ptr) {
        if ((rights_base & WasiRights.FD_READDIR) == 0)
            return WasiErrno.NOTCAPABLE;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            int written = 0;
            long current_cookie = 0;
            for (Path entry : stream) {
                if (current_cookie >= cookie) {
                    String name = entry.getFileName().toString();
                    byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                    int entry_len = 24 + nameBytes.length;

                    if (written + entry_len > buf_len)
                        break;

                    int base = buf_ptr + written;
                    memory.writeLong(base, current_cookie + 1); // next cookie
                    BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                    memory.writeLong(base + 8, attrs.fileKey() != null ? (long) attrs.fileKey().hashCode() : 0);
                    memory.writeInt(base + 16, nameBytes.length);
                    memory.write(base + 20, (byte) WasiFileType.getWasiFileType(attrs));
                    memory.write(base + 24, nameBytes);

                    written += entry_len;
                }
                current_cookie++;
            }
            memory.writeInt(nwritten_ptr, written);
            return WasiErrno.SUCCESS;
        } catch (IOException e) {
            return WasiErrno.IO;
        }
    }

    @Override
    public int getType() {
        return WasiFileType.DIRECTORY;
    }

    @Override
    public Path getPath() {
        return path;
    }
}
