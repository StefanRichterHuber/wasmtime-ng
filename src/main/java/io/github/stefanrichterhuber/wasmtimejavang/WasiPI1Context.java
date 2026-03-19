package io.github.stefanrichterhuber.wasmtimejavang;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.stefanrichterhuber.wasmtimejavang.wasip1.DirectoryWasiFileDescriptor;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.FileWasiFileDescriptor;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.NoOpInputStream;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.NoOpOutputStream;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.ProcExitException;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.ServerSocketWasiFileDescriptor;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.SocketWasiFileDescriptor;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.StdinWasiFileDescriptor;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.StdoutWasiFileDescriptor;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.WasiErrno;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.WasiFileDescriptor;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.WasiPI1Util;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.WasiRights;

/**
 * Implementation of WASI Snapshot Preview 1.
 * This class provides the necessary imports for a WASM module to interact with
 * the host system
 * following the WASI (WebAssembly System Interface) specification.
 * It handles environment variables, command-line arguments, clocks, random
 * numbers, and filesystem access.
 */
public class WasiPI1Context implements WasmContext {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Module for all WASIPI1 imported functions
     */
    private static final String WASI_SNAPSHOT_PREVIEW1_MODULE = "wasi_snapshot_preview1";

    /**
     * Charset used for all strings read from / written to the wasm memory
     */
    private static final Charset STD_CHARSET = StandardCharsets.UTF_8;

    /**
     * The main memory of the application
     */
    private static final String STD_MEMORY = "memory";

    /**
     * Environment variables passed to the WASM module.
     * <br>
     * Thread safety: Read-only after start
     */
    private final Map<String, String> env = new HashMap<>();

    /**
     * Command-line arguments passed to the WASM module.
     * <br>
     * Thread safety: Read-only after start
     */
    private final List<String> args = new ArrayList<>();

    /**
     * The clock used for time-related operations.
     * <br>
     * Thread safety: Read-only after start
     */
    private Clock clock = Clock.systemDefaultZone();

    /**
     * The input stream used for stdin (file descriptor 0).
     * <br>
     * Thread safety: Read-only after start
     */
    private InputStream stdin = new NoOpInputStream();

    /**
     * The output stream used for stdout (file descriptor 1).
     * <br>
     * Thread safety: Read-only after start
     */
    private OutputStream stdout = new NoOpOutputStream();

    /**
     * The output stream used for stderr (file descriptor 2).
     * <br>
     * Thread safety: Read-only after start
     */
    private OutputStream stderr = new NoOpOutputStream();

    /**
     * The random number generator used for random_get.
     * <br>
     * Thread safety: Read-only after start
     */
    private Random random = new Random();

    /**
     * Maps directory paths within the WASM client to Path objects on the host
     * system.
     * Used for preopened directories.
     * <br>
     * Thread safety: Read-only after start
     */
    private Map<String, Path> pathMappings = new HashMap<>();

    /**
     * Open file descriptors.
     * <br>
     * Thread safety: Might be modified after start, when the app opens new files
     */
    private final Map<Integer, WasiFileDescriptor> fds = new ConcurrentHashMap<>();

    /**
     * Generator for the next available file descriptor.
     * <br>
     * Thread safety: Might be modified after start, when the app opens new files
     */
    private final AtomicInteger nextFd = new AtomicInteger(3);

    /**
     * Creates a new WASI Snapshot Preview 1 context.
     */
    public WasiPI1Context() {
    }

    /**
     * Resolves a relative path against a base file descriptor.
     *
     * @param fd   The base file descriptor (must be a directory).
     * @param path The relative path.
     * @return The resolved Path object, or null if resolution failed.
     */
    private Path resolvePath(int fd, String path) {
        WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return null;
        Path base = wfd.getPath();
        if (base == null)
            return null;
        if (path.isEmpty() || path.equals("."))
            return base;

        // Ensure the path is resolved and normalized within the base directory to
        // prevent path traversal
        Path resolved = base.resolve(path).normalize().toAbsolutePath();
        Path absoluteBase = base.normalize().toAbsolutePath();

        if (resolved.startsWith(absoluteBase)) {
            return resolved;
        } else {
            return null;
        }
    }

    /**
     * Initializes the standard file descriptors (stdin, stdout, stderr) and
     * preopened directories.
     */
    private synchronized void initFds() {
        if (fds.isEmpty()) {
            fds.put(0, new StdinWasiFileDescriptor(stdin));
            fds.put(1, new StdoutWasiFileDescriptor(stdout));
            fds.put(2, new StdoutWasiFileDescriptor(stderr));

            // Add preopened directories
            for (Map.Entry<String, Path> entry : pathMappings.entrySet()) {
                int fd = nextFd.getAndIncrement();
                fds.put(fd, new DirectoryWasiFileDescriptor(entry.getValue(),
                        WasiRights.PATH_OPEN | WasiRights.FD_READDIR | WasiRights.PATH_READLINK
                                | WasiRights.PATH_FILESTAT_GET | WasiRights.PATH_FILESTAT_SET_TIMES
                                | WasiRights.PATH_CREATE_DIRECTORY | WasiRights.PATH_CREATE_FILE
                                | WasiRights.PATH_LINK_SOURCE | WasiRights.PATH_LINK_TARGET
                                | WasiRights.PATH_RENAME_SOURCE | WasiRights.PATH_RENAME_TARGET
                                | WasiRights.PATH_SYMLINK | WasiRights.PATH_REMOVE_DIRECTORY
                                | WasiRights.PATH_UNLINK_FILE | WasiRights.FD_FILESTAT_GET
                                | WasiRights.FD_FILESTAT_SET_SIZE | WasiRights.FD_FILESTAT_SET_TIMES
                                | WasiRights.FD_ADVISE | WasiRights.FD_ALLOCATE | WasiRights.FD_DATASYNC
                                | WasiRights.FD_SYNC | WasiRights.FD_WRITE | WasiRights.FD_READ | WasiRights.FD_SEEK
                                | WasiRights.FD_TELL | WasiRights.FD_FDSTAT_SET_FLAGS | WasiRights.POLL_FD_READWRITE
                                | WasiRights.PATH_FILESTAT_SET_SIZE,
                        WasiRights.PATH_OPEN | WasiRights.FD_READDIR | WasiRights.PATH_READLINK
                                | WasiRights.PATH_FILESTAT_GET | WasiRights.PATH_FILESTAT_SET_TIMES
                                | WasiRights.PATH_CREATE_DIRECTORY | WasiRights.PATH_CREATE_FILE
                                | WasiRights.PATH_LINK_SOURCE | WasiRights.PATH_LINK_TARGET
                                | WasiRights.PATH_RENAME_SOURCE | WasiRights.PATH_RENAME_TARGET
                                | WasiRights.PATH_SYMLINK | WasiRights.PATH_REMOVE_DIRECTORY
                                | WasiRights.PATH_UNLINK_FILE | WasiRights.FD_FILESTAT_GET
                                | WasiRights.FD_FILESTAT_SET_SIZE | WasiRights.FD_FILESTAT_SET_TIMES
                                | WasiRights.FD_ADVISE | WasiRights.FD_ALLOCATE | WasiRights.FD_DATASYNC
                                | WasiRights.FD_SYNC | WasiRights.FD_WRITE | WasiRights.FD_READ | WasiRights.FD_SEEK
                                | WasiRights.FD_TELL | WasiRights.FD_FDSTAT_SET_FLAGS | WasiRights.POLL_FD_READWRITE
                                | WasiRights.PATH_FILESTAT_SET_SIZE));
            }
        }
    }

    /**
     * Maps a host directory to a client-visible path.
     *
     * @param host   The host Path.
     * @param client The path as seen by the WASM module.
     * @return This context.
     */
    public WasiPI1Context withDirectory(Path host, String client) {
        pathMappings.put(client, host);
        return this;
    }

    /**
     * Preopens a socket and makes it available to the WASM module.
     * 
     * @param socket The socket.
     * @return This context.
     */
    public WasiPI1Context withSocket(java.net.Socket socket) {
        initFds();
        try {
            int fd = nextFd.getAndIncrement();
            fds.put(fd, new SocketWasiFileDescriptor(socket,
                    WasiRights.FD_READ | WasiRights.FD_WRITE | WasiRights.FD_FILESTAT_GET | WasiRights.FD_ADVISE
                            | WasiRights.FD_DATASYNC | WasiRights.FD_SYNC | WasiRights.FD_TELL | WasiRights.FD_SEEK,
                    0));
        } catch (Exception e) {
            throw new RuntimeException("Failed to wrap socket", e);
        }
        return this;
    }

    /**
     * Preopens a server socket and makes it available to the WASM module.
     * 
     * @param serverSocket The server socket.
     * @return This context.
     */
    public WasiPI1Context withServerSocket(java.net.ServerSocket serverSocket) {
        initFds();
        int fd = nextFd.getAndIncrement();
        fds.put(fd, new ServerSocketWasiFileDescriptor(serverSocket,
                WasiRights.PATH_OPEN | WasiRights.FD_READDIR,
                WasiRights.FD_READ | WasiRights.FD_WRITE | WasiRights.FD_FILESTAT_GET | WasiRights.FD_ADVISE
                        | WasiRights.FD_DATASYNC | WasiRights.FD_SYNC | WasiRights.FD_TELL | WasiRights.FD_SEEK,
                s -> {
                    try {
                        return new SocketWasiFileDescriptor(s,
                                WasiRights.FD_READ | WasiRights.FD_WRITE | WasiRights.FD_FILESTAT_GET
                                        | WasiRights.FD_ADVISE | WasiRights.FD_DATASYNC | WasiRights.FD_SYNC
                                        | WasiRights.FD_TELL | WasiRights.FD_SEEK,
                                0);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
        return this;
    }

    /**
     * Sets the random number generator.
     *
     * @param random The random number generator.
     * @return This context.
     */
    public WasiPI1Context withRandom(Random random) {
        this.random = random;
        return this;
    }

    /**
     * Sets the environment variables.
     *
     * @param env A map of environment variables.
     * @return This context.
     */
    public WasiPI1Context withEnvs(Map<String, String> env) {
        this.env.putAll(env);
        return this;
    }

    /**
     * Sets a single environment variable.
     *
     * @param key   The variable key.
     * @param value The variable value.
     * @return This context.
     */
    public WasiPI1Context withEnvironment(String key, String value) {
        this.env.put(key, value);
        return this;
    }

    /**
     * Sets the command-line arguments.
     *
     * @param args A list of arguments.
     * @return This context.
     */
    public WasiPI1Context withArguments(List<String> args) {
        this.args.addAll(args);
        return this;
    }

    /**
     * Sets the clock.
     *
     * @param clock The clock.
     * @return This context.
     */
    public WasiPI1Context withClock(Clock clock) {
        this.clock = clock;
        return this;
    }

    /**
     * Sets the standard output stream.
     *
     * @param stdout The stdout stream.
     * @return This context.
     */
    public WasiPI1Context withStdOut(OutputStream stdout) {
        this.stdout = stdout;
        return this;
    }

    /**
     * Sets the standard error stream.
     *
     * @param stderr The stderr stream.
     * @return This context.
     */
    public WasiPI1Context withStdErr(OutputStream stderr) {
        this.stderr = stderr;
        return this;
    }

    /**
     * Sets the standard input stream.
     *
     * @param stdin The stdin stream.
     * @return This context.
     */
    public WasiPI1Context withStdIn(InputStream stdin) {
        this.stdin = stdin;
        return this;
    }

    /**
     * Returns the list of WASI import functions.
     * This method initializes the file descriptors upon the first call.
     *
     * @return A list of ImportFunction objects.
     */
    @Override
    public List<ImportFunction> getImportFunctions() {
        initFds();

        List<ImportFunction> result = new ArrayList<>();

        // poll_oneoff (i32, i32, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "poll_oneoff",
                List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I32),
                List.of(ValType.I32), this::pollOneoff));

        // clock_time_get (i32, i64, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "clock_time_get",
                List.of(ValType.I32, ValType.I64, ValType.I32),
                List.of(ValType.I32), this::clockTimeGet));

        // clock_res_get (i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "clock_res_get",
                List.of(ValType.I32, ValType.I32),
                List.of(ValType.I32), this::clockResGet));

        // fd_write (i32, i32, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_write",
                List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I32),
                List.of(ValType.I32), this::fdWrite));

        // fd_read (i32, i32, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_read",
                List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I32),
                List.of(ValType.I32), this::fdRead));

        // args_get (i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "args_get", List.of(ValType.I32, ValType.I32),
                List.of(ValType.I32), this::argsGet));

        // args_sizes_get (i32, i32) -> i32
        result.add(
                new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "args_sizes_get", List.of(ValType.I32, ValType.I32),
                        List.of(ValType.I32), this::argsSizesGet));

        // environ_get (i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "environ_get", List.of(ValType.I32, ValType.I32),
                List.of(ValType.I32), this::environGet));

        // environ_sizes_get (i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "environ_sizes_get",
                List.of(ValType.I32, ValType.I32),
                List.of(ValType.I32), this::environSizesGet));

        // random_get (i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "random_get", List.of(ValType.I32, ValType.I32),
                List.of(ValType.I32), this::randomGet));

        // proc_exit (i32) -> nil
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "proc_exit", List.of(ValType.I32),
                List.of(), this::procExit));

        // fd_advise (i32, i64, i64, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_advise",
                List.of(ValType.I32, ValType.I64, ValType.I64, ValType.I32), List.of(ValType.I32),
                this::fdAdvise));

        // fd_allocate (i32, i64, i64) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_allocate",
                List.of(ValType.I32, ValType.I64, ValType.I64), List.of(ValType.I32),
                this::fdAllocate));

        // fd_close (i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_close", List.of(ValType.I32),
                List.of(ValType.I32), this::fdClose));

        // fd_datasync (i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_datasync", List.of(ValType.I32),
                List.of(ValType.I32), this::fdDatasync));

        // fd_fdstat_get (i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_fdstat_get", List.of(ValType.I32, ValType.I32),
                List.of(ValType.I32), this::fdFdstatGet));

        // fd_fdstat_set_flags (i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_fdstat_set_flags",
                List.of(ValType.I32, ValType.I32),
                List.of(ValType.I32), this::fdFdstatSetFlags));

        // fd_fdstat_set_rights (i32, i64, i64) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_fdstat_set_rights",
                List.of(ValType.I32, ValType.I64, ValType.I64), List.of(ValType.I32),
                this::fdFdstatSetRights));

        // fd_filestat_get (i32, i32) -> i32
        result.add(
                new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_filestat_get", List.of(ValType.I32, ValType.I32),
                        List.of(ValType.I32), this::fdFilestatGet));

        // fd_filestat_set_size (i32, i64) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_filestat_set_size",
                List.of(ValType.I32, ValType.I64), List.of(ValType.I32),
                this::fdFilestatSetSize));

        // fd_filestat_set_times (i32, i64, i64, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_filestat_set_times",
                List.of(ValType.I32, ValType.I64, ValType.I64, ValType.I32), List.of(ValType.I32),
                this::fdFilestatSetTimes));

        // fd_pread (i32, i32, i32, i64, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_pread",
                List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I64, ValType.I32), List.of(ValType.I32),
                this::fdPread));

        // fd_prestat_dir_name (i32, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_prestat_dir_name",
                List.of(ValType.I32, ValType.I32, ValType.I32), List.of(ValType.I32),
                this::fdPrestatDirName));

        // fd_prestat_get (i32, i32) -> i32
        result.add(
                new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_prestat_get", List.of(ValType.I32, ValType.I32),
                        List.of(ValType.I32), this::fdPrestatGet));

        // fd_pwrite (i32, i32, i32, i64, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_pwrite",
                List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I64, ValType.I32), List.of(ValType.I32),
                this::fdPwrite));

        // fd_readdir (i32, i32, i32, i64, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_readdir",
                List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I64, ValType.I32), List.of(ValType.I32),
                this::fdReaddir));

        // fd_renumber (i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_renumber", List.of(ValType.I32, ValType.I32),
                List.of(ValType.I32), this::fdRenumber));

        // fd_seek (i32, i64, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_seek",
                List.of(ValType.I32, ValType.I64, ValType.I32, ValType.I32), List.of(ValType.I32),
                this::fdSeek));

        // fd_sync (i32) -> i32
        result.add(
                new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_sync", List.of(ValType.I32), List.of(ValType.I32),
                        this::fdSync));

        // fd_tell (i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "fd_tell", List.of(ValType.I32, ValType.I32),
                List.of(ValType.I32), this::fdTell));

        // path_create_directory (i32, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "path_create_directory",
                List.of(ValType.I32, ValType.I32, ValType.I32), List.of(ValType.I32),
                this::pathCreateDirectory));

        // path_filestat_get (i32, i32, i32, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "path_filestat_get",
                List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I32), List.of(ValType.I32),
                this::pathFilestatGet));

        // path_filestat_set_times (i32, i32, i32, i32, i64, i64, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "path_filestat_set_times",
                List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I64, ValType.I64, ValType.I32),
                List.of(ValType.I32), this::pathFilestatSetTimes));

        // path_link (i32, i32, i32, i32, i32, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "path_link",
                List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I32),
                List.of(ValType.I32), this::pathLink));

        // path_open (i32, i32, i32, i32, i32, i64, i64, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "path_open",
                List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I64, ValType.I64,
                        ValType.I32, ValType.I32),
                List.of(ValType.I32), this::pathOpen));

        // path_readlink (i32, i32, i32, i32, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "path_readlink",
                List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I32),
                List.of(ValType.I32), this::pathReadlink));

        // path_remove_directory (i32, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "path_remove_directory",
                List.of(ValType.I32, ValType.I32, ValType.I32), List.of(ValType.I32),
                this::pathRemoveDirectory));

        // path_rename (i32, i32, i32, i32, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "path_rename",
                List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I32),
                List.of(ValType.I32), this::pathRename));

        // path_symlink (i32, i32, i32, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "path_symlink",
                List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I32), List.of(ValType.I32),
                this::pathSymlink));

        // path_unlink_file (i32, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "path_unlink_file",
                List.of(ValType.I32, ValType.I32, ValType.I32), List.of(ValType.I32),
                this::pathUnlinkFile));

        // sched_yield () -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "sched_yield", List.of(), List.of(ValType.I32),
                this::schedYield));

        // proc_raise (i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "proc_raise", List.of(ValType.I32),
                List.of(ValType.I32), this::procRaise));

        // sock_recv (i32, i32, i32, i32, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "sock_recv",
                List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I32),
                List.of(ValType.I32), this::sockRecv));

        // sock_send (i32, i32, i32, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "sock_send",
                List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I32, ValType.I32),
                List.of(ValType.I32), this::sockSend));

        // sock_shutdown (i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "sock_shutdown", List.of(ValType.I32, ValType.I32),
                List.of(ValType.I32), this::sockShutdown));

        // sock_accept (i32, i32, i32) -> i32
        result.add(new ImportFunction(WASI_SNAPSHOT_PREVIEW1_MODULE, "sock_accept",
                List.of(ValType.I32, ValType.I32, ValType.I32),
                List.of(ValType.I32), this::sockAccept));

        return result;
    }

    /**
     * Implementation of WASI poll_oneoff.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] pollOneoff(WasmtimeInstance instance, Object[] args) {
        final int in_ptr = (int) args[0];
        final int out_ptr = (int) args[1];
        final int nsubscriptions = (int) args[2];
        final int nevents_ptr = (int) args[3];

        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        if (memory != null) {
            // Write nsubscriptions to nevents_ptr
            memory.writeInt(nevents_ptr, nsubscriptions);

            // For each subscription, write a success event
            for (int i = 0; i < nsubscriptions; i++) {
                // Read userdata from subscription
                final byte[] userdata = memory.read(in_ptr + (i * 48), 8);

                final byte[] event = new byte[16];
                // userdata is at offset 0 (8 bytes)
                System.arraycopy(userdata, 0, event, 0, 8);
                // error is at offset 8 (2 bytes) - 0 is success
                // type is at offset 10 (1 byte) - 0 is clock
                memory.write(out_ptr + (i * 16), event);
            }
        }
        return new Object[] { 0 };
    }

    /**
     * Implementation of WASI clock_time_get.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] clockTimeGet(WasmtimeInstance instance, Object[] args) {
        final int clock_id = (int) args[0];
        final int time_ptr = (int) args[2];
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        if (memory != null) {
            long now;
            if (clock_id == 0) {
                // realtime
                now = this.clock.millis() * 1000000L;
            } else {
                // monotonic
                now = System.nanoTime();
            }
            memory.writeLong(time_ptr, now);
        }
        return new Object[] { 0 };
    }

    /**
     * Implementation of WASI clock_res_get.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] clockResGet(WasmtimeInstance instance, Object[] args) {
        final int time_ptr = (int) args[1];
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        if (memory != null) {
            // Resolution is 1ms for realtime, 1ns for monotonic (usually)
            // But we return 1ns for both as a safe bet for modern systems
            memory.writeLong(time_ptr, 1L);
        }
        return new Object[] { 0 };
    }

    /**
     * Implementation of WASI fd_write.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdWrite(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int iovs_ptr = (int) args[1];
        final int iovs_len = (int) args[2];
        final int nwritten_ptr = (int) args[3];

        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null) {
            return new Object[] { WasiErrno.BADF };
        }

        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        return new Object[] { wfd.fd_write(memory, iovs_ptr, iovs_len, nwritten_ptr) };
    }

    /**
     * Implementation of WASI fd_read.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdRead(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int iovs_ptr = (int) args[1];
        final int iovs_len = (int) args[2];
        final int nread_ptr = (int) args[3];

        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null) {
            return new Object[] { WasiErrno.BADF };
        }

        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        return new Object[] { wfd.fd_read(memory, iovs_ptr, iovs_len, nread_ptr) };
    }

    /**
     * Implementation of WASI args_get.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] argsGet(WasmtimeInstance instance, Object[] args) {
        final int argv_ptr = (int) args[0];
        final int argv_buf_ptr = (int) args[1];
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        if (memory != null) {
            int current_argv_ptr = argv_ptr;
            int current_argv_buf_ptr = argv_buf_ptr;

            for (String arg : this.args) {
                // Write pointer to the argument string
                memory.writeInt(current_argv_ptr, current_argv_buf_ptr);

                // Write the argument string
                final int len = memory.writeCString(current_argv_buf_ptr, arg, STD_CHARSET);
                current_argv_buf_ptr += len;
                current_argv_ptr += 4;
            }
        }
        return new Object[] { 0 };
    }

    /**
     * Implementation of WASI args_sizes_get.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] argsSizesGet(WasmtimeInstance instance, Object[] args) {
        final int count_ptr = (int) args[0];
        final int buf_size_ptr = (int) args[1];
        WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        if (memory != null) {
            final int count = this.args.size();
            int buf_size = 0;
            for (String arg : this.args) {
                buf_size += arg.getBytes(STD_CHARSET).length + 1; // +1 for null
                                                                  // terminator
            }

            memory.writeInt(count_ptr, count);
            memory.writeInt(buf_size_ptr, buf_size);
        }
        return new Object[] { 0 };
    }

    /**
     * Implementation of WASI environ_get.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] environGet(WasmtimeInstance instance, Object[] args) {
        final int environ_ptr = (int) args[0];
        final int environ_buf_ptr = (int) args[1];
        WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        if (memory != null) {
            int current_environ_ptr = environ_ptr;
            int current_environ_buf_ptr = environ_buf_ptr;

            for (Map.Entry<String, String> entry : env.entrySet()) {
                // Write pointer to the environment variable string
                memory.writeInt(current_environ_ptr, current_environ_buf_ptr);

                // Write the environment variable string
                final String s = entry.getKey() + "=" + entry.getValue();

                final int len = memory.writeCString(current_environ_buf_ptr, s, STD_CHARSET);
                current_environ_buf_ptr += len;
                current_environ_ptr += 4;
            }
        }
        return new Object[] { 0 };
    }

    /**
     * Implementation of WASI environ_sizes_get.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] environSizesGet(WasmtimeInstance instance, Object[] args) {
        final int count_ptr = (int) args[0];
        final int buf_size_ptr = (int) args[1];
        WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        if (memory != null) {
            final int count = env.size();
            int buf_size = 0;
            for (Map.Entry<String, String> entry : env.entrySet()) {
                String s = entry.getKey() + "=" + entry.getValue();
                buf_size += s.getBytes(STD_CHARSET).length + 1; // +1 for null terminator
            }

            memory.writeInt(count_ptr, count);
            memory.writeInt(buf_size_ptr, buf_size);
        }
        return new Object[] { 0 };
    }

    /**
     * Implementation of WASI random_get.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] randomGet(WasmtimeInstance instance, Object[] args) {
        final int buf_ptr = (int) args[0];
        final int buf_len = (int) args[1];
        WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        if (memory != null) {
            final byte[] bytes = new byte[buf_len];
            this.random.nextBytes(bytes);
            memory.write(buf_ptr, bytes);
        }
        return new Object[] { WasiErrno.SUCCESS };
    }

    /**
     * Implementation of WASI proc_exit.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] procExit(WasmtimeInstance instance, Object[] args) {
        LOGGER.debug("Wasm program called proc_exit with status code {}", (int) args[0]);
        throw new ProcExitException((int) args[0]);
    }

    /**
     * Implementation of WASI fd_advise.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdAdvise(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final long offset = (long) args[1];
        final long len = (long) args[2];
        final int advice = (int) args[3];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        return new Object[] { wfd.fd_advise(offset, len, advice) };
    }

    /**
     * Implementation of WASI fd_allocate.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdAllocate(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final long offset = (long) args[1];
        final long len = (long) args[2];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        return new Object[] { wfd.fd_allocate(offset, len) };
    }

    /**
     * Implementation of WASI fd_close.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdClose(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final WasiFileDescriptor wfd = fds.remove(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        try {
            wfd.close();
        } catch (Exception e) {
            return new Object[] { WasiErrno.IO };
        }
        return new Object[] { WasiErrno.SUCCESS };
    }

    /**
     * Implementation of WASI fd_datasync.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdDatasync(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        return new Object[] { wfd.fd_datasync() };
    }

    /**
     * Implementation of WASI fd_fdstat_get.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdFdstatGet(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int ptr = (int) args[1];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        return new Object[] { wfd.fd_fdstat_get(memory, ptr) };
    }

    /**
     * Implementation of WASI fd_fdstat_set_flags.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdFdstatSetFlags(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int flags = (int) args[1];
        WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        return new Object[] { wfd.fd_fdstat_set_flags(flags) };
    }

    /**
     * Implementation of WASI fd_fdstat_set_rights.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdFdstatSetRights(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final long rights_base = (long) args[1];
        final long rights_inheriting = (long) args[2];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        return new Object[] { wfd.fd_fdstat_set_rights(rights_base, rights_inheriting) };
    }

    /**
     * Implementation of WASI fd_filestat_get.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdFilestatGet(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int ptr = (int) args[1];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        return new Object[] { wfd.fd_filestat_get(memory, ptr) };
    }

    /**
     * Implementation of WASI fd_filestat_set_size.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdFilestatSetSize(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final long size = (long) args[1];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        return new Object[] { wfd.fd_filestat_set_size(size) };
    }

    /**
     * Implementation of WASI fd_filestat_set_times.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdFilestatSetTimes(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final long atim = (long) args[1];
        final long mtim = (long) args[2];
        final int fst_flags = (int) args[3];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        return new Object[] { wfd.fd_filestat_set_times(atim, mtim, fst_flags) };
    }

    /**
     * Implementation of WASI fd_pread.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdPread(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int iovs_ptr = (int) args[1];
        final int iovs_len = (int) args[2];
        final long offset = (long) args[3];
        final int nread_ptr = (int) args[4];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        return new Object[] { wfd.fd_pread(memory, iovs_ptr, iovs_len, offset, nread_ptr) };
    }

    /**
     * Implementation of WASI fd_prestat_dir_name.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdPrestatDirName(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int path_ptr = (int) args[1];
        final int path_len = (int) args[2];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        Path path = wfd.getPath();
        if (path == null)
            return new Object[] { WasiErrno.BADF };

        // Find the client path for this host path
        String clientPath = null;
        for (Map.Entry<String, Path> entry : pathMappings.entrySet()) {
            if (entry.getValue().equals(path)) {
                clientPath = entry.getKey();
                break;
            }
        }
        if (clientPath == null)
            return new Object[] { WasiErrno.BADF };

        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        if (memory != null) {
            byte[] bytes = clientPath.getBytes(STD_CHARSET);
            memory.write(path_ptr, java.util.Arrays.copyOf(bytes, Math.min(bytes.length, path_len)));
        }
        return new Object[] { WasiErrno.SUCCESS };
    }

    /**
     * Implementation of WASI fd_prestat_get.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdPrestatGet(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int ptr = (int) args[1];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        final Path path = wfd.getPath();
        if (path == null)
            return new Object[] { WasiErrno.BADF };

        // Find the client path for this host path
        String clientPath = null;
        for (Map.Entry<String, Path> entry : pathMappings.entrySet()) {
            if (entry.getValue().equals(path)) {
                clientPath = entry.getKey();
                break;
            }
        }
        if (clientPath == null)
            return new Object[] { WasiErrno.BADF };

        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        if (memory != null) {
            memory.write(ptr, (byte) 0); // pr_type = prestat_dir (0)
            memory.writeInt(ptr + 4, clientPath.getBytes(STD_CHARSET).length);
        }
        return new Object[] { WasiErrno.SUCCESS };
    }

    /**
     * Implementation of WASI fd_pwrite.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdPwrite(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int iovs_ptr = (int) args[1];
        final int iovs_len = (int) args[2];
        final long offset = (long) args[3];
        final int nwritten_ptr = (int) args[4];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        return new Object[] { wfd.fd_pwrite(memory, iovs_ptr, iovs_len, offset, nwritten_ptr) };
    }

    /**
     * Implementation of WASI fd_readdir.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdReaddir(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int buf_ptr = (int) args[1];
        final int buf_len = (int) args[2];
        final long cookie = (long) args[3];
        final int nwritten_ptr = (int) args[4];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        return new Object[] { wfd.fd_readdir(memory, buf_ptr, buf_len, cookie, nwritten_ptr) };
    }

    /**
     * Implementation of WASI fd_renumber.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdRenumber(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int to = (int) args[1];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        final WasiFileDescriptor wfd_to = fds.remove(to);
        if (wfd_to != null) {
            try {
                wfd_to.close();
            } catch (Exception e) {
                // Ignore
            }
        }
        fds.put(to, fds.remove(fd));
        return new Object[] { WasiErrno.SUCCESS };
    }

    /**
     * Implementation of WASI fd_seek.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdSeek(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final long offset = (long) args[1];
        final int whence = (int) args[2];
        final int newoffset_ptr = (int) args[3];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        return new Object[] { wfd.fd_seek(offset, whence, newoffset_ptr, memory) };
    }

    /**
     * Implementation of WASI fd_sync.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdSync(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        return new Object[] { wfd.fd_sync() };
    }

    /**
     * Implementation of WASI fd_tell.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] fdTell(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int newoffset_ptr = (int) args[1];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        return new Object[] { wfd.fd_tell(newoffset_ptr, memory) };
    }

    /**
     * Implementation of WASI path_create_directory.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] pathCreateDirectory(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int path_ptr = (int) args[1];
        final int path_len = (int) args[2];
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        final String pathStr = memory.readString(path_ptr, path_len, STD_CHARSET);
        final Path path = resolvePath(fd, pathStr);
        if (path == null)
            return new Object[] { WasiErrno.BADF };
        try {
            Files.createDirectory(path);
            return new Object[] { WasiErrno.SUCCESS };
        } catch (IOException e) {
            return new Object[] { WasiErrno.IO };
        }
    }

    /**
     * Implementation of WASI path_filestat_get.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] pathFilestatGet(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int flags = (int) args[1];
        final int path_ptr = (int) args[2];
        final int path_len = (int) args[3];
        final int ptr = (int) args[4];
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        final String pathStr = memory.readString(path_ptr, path_len, STD_CHARSET);
        final Path path = resolvePath(fd, pathStr);
        if (path == null)
            return new Object[] { WasiErrno.BADF };
        try {
            final BasicFileAttributes attrs = (flags & 1) != 0
                    ? Files.readAttributes(path, BasicFileAttributes.class)
                    : Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            WasiPI1Util.writeFilestat(memory, ptr, attrs);
            return new Object[] { WasiErrno.SUCCESS };
        } catch (IOException e) {
            return new Object[] { WasiErrno.NOENT };
        }
    }

    /**
     * Implementation of WASI path_filestat_set_times.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] pathFilestatSetTimes(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        @SuppressWarnings("unused")
        final int flags = (int) args[1];
        final int path_ptr = (int) args[2];
        final int path_len = (int) args[3];
        final long atim = (long) args[4];
        final long mtim = (long) args[5];
        final int fst_flags = (int) args[6];
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        final String pathStr = memory.readString(path_ptr, path_len, STD_CHARSET);
        final Path path = resolvePath(fd, pathStr);
        if (path == null)
            return new Object[] { WasiErrno.BADF };
        return new Object[] { WasiPI1Util.setFileTimes(path, atim, mtim, fst_flags) };
    }

    /**
     * Implementation of WASI path_link.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] pathLink(WasmtimeInstance instance, Object[] args) {
        final int old_fd = (int) args[0];
        @SuppressWarnings("unused")
        final int old_flags = (int) args[1];
        final int old_path_ptr = (int) args[2];
        final int old_path_len = (int) args[3];
        final int new_fd = (int) args[4];
        final int new_path_ptr = (int) args[5];
        final int new_path_len = (int) args[6];
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        final String oldPathStr = memory.readString(old_path_ptr, old_path_len,
                STD_CHARSET);
        final String newPathStr = memory.readString(new_path_ptr, new_path_len,
                STD_CHARSET);
        final Path oldPath = resolvePath(old_fd, oldPathStr);
        final Path newPath = resolvePath(new_fd, newPathStr);
        if (oldPath == null || newPath == null)
            return new Object[] { WasiErrno.BADF };
        try {
            Files.createLink(newPath, oldPath);
            return new Object[] { WasiErrno.SUCCESS };
        } catch (IOException e) {
            return new Object[] { WasiErrno.IO };
        }
    }

    /**
     * Implementation of WASI path_open.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] pathOpen(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        @SuppressWarnings("unused")
        final int dirflags = (int) args[1];
        final int path_ptr = (int) args[2];
        final int path_len = (int) args[3];
        final int oflags = (int) args[4];
        final long fs_rights_base = (long) args[5];
        final long fs_rights_inheriting = (long) args[6];
        final int fdflags = (int) args[7];
        final int opened_fd_ptr = (int) args[8];

        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        final String pathStr = memory.readString(path_ptr, path_len, STD_CHARSET);
        final Path path = resolvePath(fd, pathStr);
        if (path == null)
            return new Object[] { WasiErrno.BADF };

        try {
            if ((oflags & 2) != 0) { // O_DIRECTORY
                if (!Files.isDirectory(path))
                    return new Object[] { WasiErrno.NOTDIR };
                final int new_fd = nextFd.getAndIncrement();
                fds.put(new_fd,
                        new DirectoryWasiFileDescriptor(path, fs_rights_base, fs_rights_inheriting));
                memory.writeInt(opened_fd_ptr, new_fd);
                return new Object[] { WasiErrno.SUCCESS };
            }

            Set<OpenOption> options = new HashSet<>();
            if ((fs_rights_base & WasiRights.FD_READ) != 0)
                options.add(StandardOpenOption.READ);
            if ((fs_rights_base & WasiRights.FD_WRITE) != 0)
                options.add(StandardOpenOption.WRITE);
            if ((oflags & 1) != 0)
                options.add(StandardOpenOption.CREATE); // O_CREAT
            if ((oflags & 4) != 0)
                options.add(StandardOpenOption.CREATE_NEW); // O_EXCL
            if ((oflags & 8) != 0)
                options.add(StandardOpenOption.TRUNCATE_EXISTING); // O_TRUNC
            if ((fdflags & 1) != 0)
                options.add(StandardOpenOption.APPEND); // APPEND

            if (Files.isDirectory(path)) {
                final int new_fd = nextFd.getAndIncrement();
                fds.put(new_fd,
                        new DirectoryWasiFileDescriptor(path, fs_rights_base, fs_rights_inheriting));
                memory.writeInt(opened_fd_ptr, new_fd);
                return new Object[] { WasiErrno.SUCCESS };
            }

            final FileChannel channel = FileChannel.open(path, options);
            final int new_fd = nextFd.getAndIncrement();
            fds.put(new_fd, new FileWasiFileDescriptor(path, channel, fdflags, fs_rights_base,
                    fs_rights_inheriting));
            memory.writeInt(opened_fd_ptr, new_fd);
            return new Object[] { WasiErrno.SUCCESS };
        } catch (IOException e) {
            return new Object[] { WasiErrno.IO };
        }
    }

    /**
     * Implementation of WASI path_readlink.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] pathReadlink(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int path_ptr = (int) args[1];
        final int path_len = (int) args[2];
        final int buf_ptr = (int) args[3];
        final int buf_len = (int) args[4];
        final int nread_ptr = (int) args[5];
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        final String pathStr = memory.readString(path_ptr, path_len, STD_CHARSET);
        final Path path = resolvePath(fd, pathStr);
        if (path == null)
            return new Object[] { WasiErrno.BADF };
        try {
            final Path target = Files.readSymbolicLink(path);
            final byte[] bytes = target.toString().getBytes(STD_CHARSET);
            final int toWrite = Math.min(bytes.length, buf_len);
            memory.write(buf_ptr, java.util.Arrays.copyOf(bytes, toWrite));
            memory.writeInt(nread_ptr, toWrite);
            return new Object[] { WasiErrno.SUCCESS };
        } catch (IOException e) {
            return new Object[] { WasiErrno.IO };
        }
    }

    /**
     * Implementation of WASI path_remove_directory.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] pathRemoveDirectory(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int path_ptr = (int) args[1];
        final int path_len = (int) args[2];
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        final String pathStr = memory.readString(path_ptr, path_len, STD_CHARSET);
        final Path path = resolvePath(fd, pathStr);
        if (path == null)
            return new Object[] { WasiErrno.BADF };
        try {
            if (!Files.isDirectory(path))
                return new Object[] { WasiErrno.NOTDIR };
            Files.delete(path);
            return new Object[] { WasiErrno.SUCCESS };
        } catch (IOException e) {
            return new Object[] { WasiErrno.IO };
        }
    }

    /**
     * Implementation of WASI path_rename.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] pathRename(WasmtimeInstance instance, Object[] args) {
        final int old_fd = (int) args[0];
        final int old_path_ptr = (int) args[1];
        final int old_path_len = (int) args[2];
        final int new_fd = (int) args[3];
        final int new_path_ptr = (int) args[4];
        final int new_path_len = (int) args[5];
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        final String oldPathStr = memory.readString(old_path_ptr, old_path_len,
                STD_CHARSET);
        final String newPathStr = memory.readString(new_path_ptr, new_path_len,
                STD_CHARSET);
        final Path oldPath = resolvePath(old_fd, oldPathStr);
        final Path newPath = resolvePath(new_fd, newPathStr);
        if (oldPath == null || newPath == null)
            return new Object[] { WasiErrno.BADF };
        try {
            Files.move(oldPath, newPath);
            return new Object[] { WasiErrno.SUCCESS };
        } catch (IOException e) {
            return new Object[] { WasiErrno.IO };
        }
    }

    /**
     * Implementation of WASI path_symlink.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] pathSymlink(WasmtimeInstance instance, Object[] args) {
        final int old_path_ptr = (int) args[0];
        final int old_path_len = (int) args[1];
        final int fd = (int) args[2];
        final int new_path_ptr = (int) args[3];
        final int new_path_len = (int) args[4];
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        final String oldPathStr = memory.readString(old_path_ptr, old_path_len,
                STD_CHARSET);
        final String newPathStr = memory.readString(new_path_ptr, new_path_len,
                STD_CHARSET);
        final Path oldPath = Paths.get(oldPathStr);
        final Path newPath = resolvePath(fd, newPathStr);
        if (newPath == null)
            return new Object[] { WasiErrno.BADF };
        try {
            Files.createSymbolicLink(newPath, oldPath);
            return new Object[] { WasiErrno.SUCCESS };
        } catch (IOException e) {
            return new Object[] { WasiErrno.IO };
        }
    }

    /**
     * Implementation of WASI path_unlink_file.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] pathUnlinkFile(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int path_ptr = (int) args[1];
        final int path_len = (int) args[2];
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);
        final String pathStr = memory.readString(path_ptr, path_len, STD_CHARSET);
        final Path path = resolvePath(fd, pathStr);
        if (path == null)
            return new Object[] { WasiErrno.BADF };
        try {
            if (Files.isDirectory(path))
                return new Object[] { WasiErrno.INVAL };
            Files.delete(path);
            return new Object[] { WasiErrno.SUCCESS };
        } catch (IOException e) {
            return new Object[] { WasiErrno.IO };
        }
    }

    /**
     * Implementation of WASI sched_yield.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] schedYield(WasmtimeInstance instance, Object[] args) {
        Thread.yield();
        return new Object[] { WasiErrno.SUCCESS };
    }

    /**
     * Implementation of WASI proc_raise.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] procRaise(WasmtimeInstance instance, Object[] args) {
        return new Object[] { WasiErrno.NOSYS };
    }

    /**
     * Implementation of WASI sock_recv.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] sockRecv(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int ri_data_ptr = (int) args[1];
        final int ri_data_len = (int) args[2];
        final int ri_flags = (int) args[3];
        final int ro_datalen_ptr = (int) args[4];
        final int ro_flags_ptr = (int) args[5];

        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);

        return new Object[] { wfd.sock_recv(memory, ri_data_ptr, ri_data_len, ri_flags, ro_datalen_ptr,
                ro_flags_ptr) };
    }

    /**
     * Implementation of WASI sock_send.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] sockSend(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int si_data_ptr = (int) args[1];
        final int si_data_len = (int) args[2];
        final int si_flags = (int) args[3];
        final int so_datalen_ptr = (int) args[4];

        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);

        return new Object[] { wfd.sock_send(memory, si_data_ptr, si_data_len, si_flags, so_datalen_ptr) };
    }

    /**
     * Implementation of WASI sock_shutdown.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] sockShutdown(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        final int how = (int) args[1];
        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        return new Object[] { wfd.sock_shutdown(how) };
    }

    /**
     * Implementation of WASI sock_accept.
     * 
     * @param instance The WasmtimeInstance calling the function.
     * 
     * @param args     The function arguments.
     * @return An array of return values.
     */
    protected Object[] sockAccept(WasmtimeInstance instance, Object[] args) {
        final int fd = (int) args[0];
        @SuppressWarnings("unused")
        final int flags = (int) args[1];
        final int fd_ptr = (int) args[2];

        final WasiFileDescriptor wfd = fds.get(fd);
        if (wfd == null)
            return new Object[] { WasiErrno.BADF };
        final WasmtimeMemory memory = instance.getMemory(STD_MEMORY);

        if (wfd instanceof ServerSocketWasiFileDescriptor) {
            try {
                ServerSocketWasiFileDescriptor swfd = (ServerSocketWasiFileDescriptor) wfd;
                java.net.Socket client = swfd.accept();
                final int new_fd = nextFd.getAndIncrement();
                fds.put(new_fd, new SocketWasiFileDescriptor(client,
                        WasiRights.FD_READ | WasiRights.FD_WRITE | WasiRights.FD_FILESTAT_GET
                                | WasiRights.FD_ADVISE | WasiRights.FD_DATASYNC | WasiRights.FD_SYNC
                                | WasiRights.FD_TELL | WasiRights.FD_SEEK,
                        0));
                memory.writeInt(fd_ptr, new_fd);
                return new Object[] { WasiErrno.SUCCESS };
            } catch (Exception e) {
                return new Object[] { WasiErrno.IO };
            }
        }

        return new Object[] { WasiErrno.NOSYS };
    }

    @Override
    public List<Importmemory> getMemories() {
        return List.of();
    }

    @Override
    public String name() {
        return WASI_SNAPSHOT_PREVIEW1_MODULE;
    }

}
