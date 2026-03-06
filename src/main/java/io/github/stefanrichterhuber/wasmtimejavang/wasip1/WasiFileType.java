package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.nio.file.attribute.BasicFileAttributes;

public class WasiFileType {
    public static final int UNKNOWN = 0;
    public static final int BLOCK_DEVICE = 1;
    public static final int CHARACTER_DEVICE = 2;
    public static final int DIRECTORY = 3;
    public static final int REGULAR_FILE = 4;
    public static final int SOCKET_DGRAM = 5;
    public static final int SOCKET_STREAM = 6;
    public static final int SYMBOLIC_LINK = 7;

    public static int getWasiFileType(BasicFileAttributes attrs) {
        if (attrs.isRegularFile())
            return REGULAR_FILE;
        if (attrs.isDirectory())
            return DIRECTORY;
        if (attrs.isSymbolicLink())
            return SYMBOLIC_LINK;
        return UNKNOWN;
    }
}
