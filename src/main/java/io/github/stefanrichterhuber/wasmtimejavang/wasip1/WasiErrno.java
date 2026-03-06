package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

/**
 * WASI error codes (preview1).
 */
public class WasiErrno {
    /** No error occurred. */
    public static final int SUCCESS = 0;
    /** Argument list too long. */
    public static final int TOOBIG = 1;
    /** Permission denied. */
    public static final int ACCES = 2;
    /** Address in use. */
    public static final int ADDRINUSE = 3;
    /** Address not available. */
    public static final int ADDRNOTAVAIL = 4;
    /** Address family not supported. */
    public static final int AFNOSUPPORT = 5;
    /** Resource temporarily unavailable. */
    public static final int AGAIN = 6;
    /** Connection already in progress. */
    public static final int ALREADY = 7;
    /** Bad file descriptor. */
    public static final int BADF = 8;
    /** Bad message. */
    public static final int BADMSG = 9;
    /** Device or resource busy. */
    public static final int BUSY = 10;
    /** Operation canceled. */
    public static final int CANCELED = 11;
    /** No child processes. */
    public static final int CHILD = 12;
    /** Connection aborted. */
    public static final int CONNABORTED = 13;
    /** Connection refused. */
    public static final int CONNREFUSED = 14;
    /** Connection reset. */
    public static final int CONNRESET = 15;
    /** Resource deadlock would occur. */
    public static final int DEADLK = 16;
    /** Message too large. */
    public static final int DESTADDRREQ = 17;
    /** Mathematics argument out of domain of function. */
    public static final int DOM = 18;
    /** Reserved. */
    public static final int DQUOT = 19;
    /** File exists. */
    public static final int EXIST = 20;
    /** Bad address. */
    public static final int FAULT = 21;
    /** File too large. */
    public static final int FBIG = 22;
    /** Host is unreachable. */
    public static final int HOSTUNREACH = 23;
    /** Identifier removed. */
    public static final int IDRM = 24;
    /** Illegal byte sequence. */
    public static final int ILSEQ = 25;
    /** Operation in progress. */
    public static final int INPROGRESS = 26;
    /** Interrupted function. */
    public static final int INTR = 27;
    /** Invalid argument. */
    public static final int INVAL = 28;
    /** I/O error. */
    public static final int IO = 29;
    /** Socket is connected. */
    public static final int ISCONN = 30;
    /** Is a directory. */
    public static final int ISDIR = 31;
    /** Too many levels of symbolic links. */
    public static final int LOOP = 32;
    /** Too many open files. */
    public static final int MFILE = 33;
    /** Too many links. */
    public static final int MLINK = 34;
    /** Message too large. */
    public static final int MSGSIZE = 35;
    /** Reserved. */
    public static final int MULTIHOP = 36;
    /** Filename too long. */
    public static final int NAMETOOLONG = 37;
    /** Network is down. */
    public static final int NETDOWN = 38;
    /** Connection aborted by network. */
    public static final int NETRESET = 39;
    /** Network unreachable. */
    public static final int NETUNREACH = 40;
    /** Too many open files in system. */
    public static final int NFILE = 41;
    /** No buffer space available. */
    public static final int NOBUFS = 42;
    /** No such device. */
    public static final int NODEV = 43;
    /** No such file or directory. */
    public static final int NOENT = 44;
    /** Executable file format error. */
    public static final int NOEXEC = 45;
    /** No locks available. */
    public static final int NOLCK = 46;
    /** Reserved. */
    public static final int NOLINK = 47;
    /** Not enough space. */
    public static final int NOMEM = 48;
    /** No message of the desired type. */
    public static final int NOMSG = 49;
    /** Protocol not available. */
    public static final int NOPROTOOPT = 50;
    /** No space left on device. */
    public static final int NOSPC = 51;
    /** Function not supported. */
    public static final int NOSYS = 52;
    /** Not a directory. */
    public static final int NOTDIR = 54;
    /** Directory not empty. */
    public static final int NOTEMPTY = 55;
    /** State not recoverable. */
    public static final int NOTRECOVERABLE = 56;
    /** Not a socket. */
    public static final int NOTSOCK = 57;
    /** Not supported, or operation not supported on socket. */
    public static final int NOTSUP = 58;
    /** Inappropriate I/O control operation. */
    public static final int NOTTY = 59;
    /** No such device or address. */
    public static final int NXIO = 60;
    /** Value too large to be stored in data type. */
    public static final int OVERFLOW = 61;
    /** Previous owner died. */
    public static final int OWNERDEAD = 62;
    /** Operation not permitted. */
    public static final int PERM = 63;
    /** Broken pipe. */
    public static final int PIPE = 64;
    /** Protocol error. */
    public static final int PROTO = 65;
    /** Protocol not supported. */
    public static final int PROTONOSUPPORT = 66;
    /** Protocol wrong type for socket. */
    public static final int PROTOTYPE = 67;
    /** Result too large. */
    public static final int RANGE = 68;
    /** Read-only file system. */
    public static final int ROFS = 69;
    /** Invalid seek. */
    public static final int SPIPE = 70;
    /** No such process. */
    public static final int SRCH = 71;
    /** Reserved. */
    public static final int STALE = 72;
    /** Connection timed out. */
    public static final int TIMEDOUT = 73;
    /** Text file busy. */
    public static final int TXTBSY = 74;
    /** Cross-device link. */
    public static final int XDEV = 75;
    /** Capabilities insufficient. */
    public static final int NOTCAPABLE = 76;
}
