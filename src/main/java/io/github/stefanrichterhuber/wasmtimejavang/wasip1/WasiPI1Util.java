package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeMemory;

public class WasiPI1Util {
    public static void writeFilestat(WasmtimeMemory memory, int ptr, BasicFileAttributes attrs) {
        memory.write(ptr, new byte[64]);
        memory.writeLong(ptr, attrs.fileKey() != null ? (long) attrs.fileKey().hashCode() : 0); // dev
        memory.writeLong(ptr + 8, attrs.fileKey() != null ? (long) attrs.fileKey().hashCode() : 0); // ino
        memory.write(ptr + 16, (byte) WasiFileType.getWasiFileType(attrs));
        // nlink at 24
        memory.writeLong(ptr + 24, 1); // nlink
        memory.writeLong(ptr + 32, attrs.size());
        memory.writeLong(ptr + 40, attrs.lastAccessTime().to(TimeUnit.NANOSECONDS));
        memory.writeLong(ptr + 48, attrs.lastModifiedTime().to(TimeUnit.NANOSECONDS));
        memory.writeLong(ptr + 56, attrs.creationTime().to(TimeUnit.NANOSECONDS));
    }

    public static int readFromInputStream(InputStream is, WasmtimeMemory memory, int iovs_ptr, int iovs_len,
            int nread_ptr) {
        try {
            int totalRead = 0;
            for (int i = 0; i < iovs_len; i++) {
                int base = memory.readInt(iovs_ptr + (i * 8));
                int len = memory.readInt(iovs_ptr + (i * 8) + 4);

                byte[] buf = new byte[len];
                int n = is.read(buf);
                if (n == -1)
                    break;
                memory.write(base, java.util.Arrays.copyOf(buf, n));
                totalRead += n;
                if (n < len)
                    break;
            }
            memory.writeInt(nread_ptr, totalRead);
            return WasiErrno.SUCCESS;
        } catch (Exception e) {
            return WasiErrno.IO;
        }
    }

    public static int writeToOutputStream(OutputStream os, WasmtimeMemory memory, int iovs_ptr, int iovs_len,
            int nwritten_ptr) {
        try {
            int totalWritten = 0;
            for (int i = 0; i < iovs_len; i++) {
                int base = memory.readInt(iovs_ptr + (i * 8));
                int len = memory.readInt(iovs_ptr + (i * 8) + 4);

                byte[] buf = memory.read(base, len);
                os.write(buf);
                totalWritten += len;
            }
            os.flush();
            memory.writeInt(nwritten_ptr, totalWritten);
            return WasiErrno.SUCCESS;
        } catch (Exception e) {
            return WasiErrno.IO;
        }
    }

    public static int setFileTimes(java.nio.file.Path path, long atim, long mtim, int fst_flags) {
        try {
            if ((fst_flags & 1) != 0) { // ATIM
                java.nio.file.attribute.FileTime time = (fst_flags & 2) != 0
                        ? java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis())
                        : java.nio.file.attribute.FileTime.from(atim, TimeUnit.NANOSECONDS);
                java.nio.file.Files.setAttribute(path, "lastAccessTime", time);
            }
            if ((fst_flags & 4) != 0) { // MTIM
                java.nio.file.attribute.FileTime time = (fst_flags & 8) != 0
                        ? java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis())
                        : java.nio.file.attribute.FileTime.from(mtim, TimeUnit.NANOSECONDS);
                java.nio.file.Files.setAttribute(path, "lastModifiedTime", time);
            }
            return WasiErrno.SUCCESS;
        } catch (java.io.IOException e) {
            return WasiErrno.IO;
        }
    }
}
