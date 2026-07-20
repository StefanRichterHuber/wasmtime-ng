package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeMemory;

public class WasiPI1Util {
    /**
     * Writes file status attributes to the specified memory location.
     * 
     * @param memory The WebAssembly memory to write to.
     * @param ptr    The memory pointer where the filestat structure should be written.
     * @param attrs  The basic file attributes to write.
     */
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

    /**
     * Reads data from an InputStream into WebAssembly memory based on an io-vector.
     * 
     * @param is        The InputStream to read from.
     * @param memory    The WebAssembly memory to write the read data to.
     * @param iovs_ptr  Pointer to the array of io-vectors.
     * @param iovs_len  Number of elements in the io-vector array.
     * @param nread_ptr Pointer to the location where the number of bytes read should be written.
     * @return {@link WasiErrno#SUCCESS} if successful, or {@link WasiErrno#IO} on an IO error.
     */
    public static int readFromInputStream(InputStream is, WasmtimeMemory memory, int iovs_ptr, int iovs_len,
            int nread_ptr) {
        try {
            int totalRead = 0;
            for (int i = 0; i < iovs_len; i++) {
                int ptr = iovs_ptr + (i * 2 * Integer.BYTES);
                int base = memory.readInt(ptr);
                int len = memory.readInt(ptr + Integer.BYTES);

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

    /**
     * Writes data from WebAssembly memory to an OutputStream based on an io-vector.
     * 
     * @param os           The OutputStream to write to.
     * @param memory       The WebAssembly memory to read data from.
     * @param iovs_ptr     Pointer to the array of io-vectors.
     * @param iovs_len     Number of elements in the io-vector array.
     * @param nwritten_ptr Pointer to the location where the number of bytes written should be written.
     * @return {@link WasiErrno#SUCCESS} if successful, or {@link WasiErrno#IO} on an IO error.
     */
    public static int writeToOutputStream(OutputStream os, WasmtimeMemory memory, int iovs_ptr, int iovs_len,
            int nwritten_ptr) {
        try {
            int totalWritten = 0;
            for (int i = 0; i < iovs_len; i++) {
                final int ptr = iovs_ptr + (i * 2 * Integer.BYTES);
                final int base = memory.readInt(ptr);
                final int len = memory.readInt(ptr + Integer.BYTES);

                final byte[] buf = memory.read(base, len);
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

    /**
     * Sets the access and modification times for a file.
     * 
     * @param path      The path to the file.
     * @param atim      The access time to set (in nanoseconds).
     * @param mtim      The modification time to set (in nanoseconds).
     * @param fst_flags Flags indicating which times to set and whether to use the current time.
     * @return {@link WasiErrno#SUCCESS} if successful, or {@link WasiErrno#IO} on an IO error.
     */
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
