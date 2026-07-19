package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

public class WasiRights {
    public static final long FD_DATASYNC = 0x0000000000000001L;
    public static final long FD_READ = 0x0000000000000002L;
    public static final long FD_SEEK = 0x0000000000000004L;
    public static final long FD_FDSTAT_SET_FLAGS = 0x0000000000000008L;
    public static final long FD_SYNC = 0x0000000000000010L;
    public static final long FD_TELL = 0x0000000000000020L;
    public static final long FD_WRITE = 0x0000000000000040L;
    public static final long FD_ADVISE = 0x0000000000000080L;
    public static final long FD_ALLOCATE = 0x0000000000000100L;
    public static final long PATH_CREATE_DIRECTORY = 0x0000000000000200L;
    public static final long PATH_CREATE_FILE = 0x0000000000000400L;
    public static final long PATH_LINK_SOURCE = 0x0000000000000800L;
    public static final long PATH_LINK_TARGET = 0x0000000000001000L;
    public static final long PATH_OPEN = 0x0000000000002000L;
    public static final long FD_READDIR = 0x0000000000004000L;
    public static final long PATH_READLINK = 0x0000000000008000L;
    public static final long PATH_RENAME_SOURCE = 0x0000000000010000L;
    public static final long PATH_RENAME_TARGET = 0x0000000000020000L;
    public static final long PATH_FILESTAT_GET = 0x0000000000040000L;
    public static final long PATH_FILESTAT_SET_SIZE = 0x0000000000080000L;
    public static final long PATH_FILESTAT_SET_TIMES = 0x0000000000100000L;
    public static final long FD_FILESTAT_GET = 0x0000000000200000L;
    public static final long FD_FILESTAT_SET_SIZE = 0x0000000000400000L;
    public static final long FD_FILESTAT_SET_TIMES = 0x0000000000800000L;
    public static final long PATH_SYMLINK = 0x0000000001000000L;
    public static final long PATH_REMOVE_DIRECTORY = 0x0000000002000000L;
    public static final long PATH_UNLINK_FILE = 0x0000000004000000L;
    public static final long POLL_FD_READWRITE = 0x0000000008000000L;
}
