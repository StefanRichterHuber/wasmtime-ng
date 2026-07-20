package io.github.stefanrichterhuber.wasmtimejavang.wasip2wasifilesystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup;
import io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitEnum;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;
import io.github.stefanrichterhuber.wasmtimejavang.internal.PathSandbox;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.WasiFileType;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasifilesystem.PreopensContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasifilesystem.TypesContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiio.WasiIoContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiio.WasiIoResources;

/**
 * Implementation of {@code wasi:filesystem/types} and
 * {@code wasi:filesystem/preopens} (WASI Preview 2, 0.2.6) -- the
 * {@code "wasi-filesystem"} component context.
 * <br>
 * Implements both generated interfaces at once (see {@link WasiRandomContext}
 * for why this works and how {@code getImportFunctions()}/
 * {@code getImportResources()}/{@code getProvidedInterfaces()} get combined).
 * <br>
 * Structured like the WASI Preview 1 filesystem support
 * ({@code io.github.stefanrichterhuber.wasmtimejavang.WasiPI1Context} +
 * {@code io.github.stefanrichterhuber.wasmtimejavang.wasip1}): a {@code
 * descriptor} resource table keyed the same way p1 keys file descriptors,
 * preopened directories configured via {@link #withDirectory(Path, String)},
 * and path resolution sandboxed by the same
 * {@link PathSandbox} both packages share. The two ABIs otherwise have
 * little in common -- p1 writes fixed-layout structs into wasm linear
 * memory, while the Component Model here exchanges {@code record}/{@code
 * variant}/{@code enum}/{@code flags} values as plain Java objects -- so
 * the actual per-function bodies are not shared.
 * <br>
 * Depends on {@code "wasi-io"} ({@link WasiIoResources}) because {@code
 * [method]descriptor.read-via-stream}/{@code write-via-stream}/{@code
 * append-via-stream} hand out {@code input-stream}/{@code output-stream}
 * resources from the very same table {@code wasi:io/streams} operates on --
 * a guest reads/writes a file by obtaining one of these streams and then
 * calling the ordinary {@code wasi:io/streams} blocking read/write
 * functions on it, exactly how Rust's {@code std::fs::File} behaves on
 * {@code wasm32-wasip2}.
 * <br>
 * Symlinks are not supported: {@code path-flags.symlink-follow} is ignored
 * (host paths are always resolved following symlinks, matching the JDK
 * default), and {@code symlink-at}/{@code readlink-at} report {@code
 * unsupported} -- WASI components don't require them to run, and sandboxed
 * filesystems like JimFS have inconsistent symlink support, so this is
 * documented as unsupported rather than implemented on uncertain footing.
 * {@code filesystem-error-code} always reports {@code Optional.empty()} for
 * the same reason {@link WasiIoContext#errorToDebugString} has no real
 * message to give: no {@code error} resource this implementation hands out
 * ever carries real failure metadata.
 */
public class WasiFilesystemContext implements TypesContext, PreopensContext {

    /** The stable name other contexts reference via {@code getDependencies()}. */
    public static final String NAME = "wasi-filesystem";

    private static final String RESOURCE_DESCRIPTOR = "descriptor";
    private static final String RESOURCE_DIRECTORY_ENTRY_STREAM = "directory-entry-stream";

    /**
     * Upper bound on how many bytes a single {@code descriptor.read} call returns.
     */
    private static final int MAX_READ_CHUNK = 65536;

    private SemanticVersion version = SemanticVersion.parse("0.2.6");

    private WasiIoResources io;

    /**
     * Maps client-visible preopen names to host directories. Thread safety:
     * read-only after linking.
     */
    private final Map<String, Path> pathMappings = new LinkedHashMap<>();

    private final Map<Integer, DescriptorEntry> descriptors = new ConcurrentHashMap<>();
    private final Map<Integer, Iterator<Path>> dirStreams = new ConcurrentHashMap<>();
    private final AtomicInteger nextRep = new AtomicInteger(1);

    private static final class DescriptorEntry {
        final Path path;
        final boolean directory;
        final FileChannel channel;
        final boolean canRead;
        final boolean canWrite;

        DescriptorEntry(Path path, boolean directory, FileChannel channel, boolean canRead, boolean canWrite) {
            this.path = path;
            this.directory = directory;
            this.channel = channel;
            this.canRead = canRead;
            this.canWrite = canWrite;
        }
    }

    /**
     * A stream reading a file starting at a fixed offset via positional
     * ({@code pread}-style) reads, so it doesn't disturb -- or get disturbed
     * by -- the shared {@link FileChannel}'s implicit position, matching
     * {@code read-via-stream}'s "independent cursor per stream" semantics.
     */
    private static final class FileChannelInputStream extends InputStream {
        private final FileChannel channel;
        private long position;

        FileChannelInputStream(FileChannel channel, long position) {
            this.channel = channel;
            this.position = position;
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int n = read(b, 0, 1);
            return n <= 0 ? -1 : (b[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
            int n = channel.read(buffer, position);
            if (n > 0) {
                position += n;
            }
            return n;
        }
    }

    /**
     * A stream writing to a file via positional ({@code pwrite}-style)
     * writes, either at a fixed offset (advancing after each write) or --
     * for {@code append-via-stream} -- always at the file's current end.
     */
    private static final class FileChannelOutputStream extends OutputStream {
        private final FileChannel channel;
        private final boolean append;
        private long position;

        FileChannelOutputStream(FileChannel channel, long position, boolean append) {
            this.channel = channel;
            this.position = position;
            this.append = append;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[] { (byte) b }, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            long writeAt = append ? channel.size() : position;
            ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
            int n = channel.write(buffer, writeAt);
            if (!append) {
                position += n;
            }
        }
    }

    /**
     * Maps a host directory to a client-visible preopen path.
     *
     * @param host   The host Path.
     * @param client The path as seen by the WASM component.
     * @return This context.
     */
    public WasiFilesystemContext withDirectory(Path host, String client) {
        pathMappings.put(client, host);
        return this;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> getProvidedInterfaces() {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(TypesContext.super.getProvidedInterfaces());
        result.addAll(PreopensContext.super.getProvidedInterfaces());
        return result;
    }

    @Override
    public List<String> getDependencies() {
        return List.of(WasiIoContext.NAME);
    }

    @Override
    public void onDependenciesResolved(ComponentContextLookup lookup) {
        this.io = (WasiIoResources) lookup.resolve(WasiIoContext.NAME, getVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "\"" + NAME + "\" requires a \"" + WasiIoContext.NAME + "\" dependency implementing "
                                + WasiIoResources.class.getSimpleName()));
    }

    @Override
    public List<ComponentImportFunction> getImportFunctions() {
        List<ComponentImportFunction> result = new ArrayList<>();
        result.addAll(TypesContext.super.getImportFunctions());
        result.addAll(PreopensContext.super.getImportFunctions());
        return result;
    }

    @Override
    public List<ComponentImportResource> getImportResources() {
        List<ComponentImportResource> result = new ArrayList<>();
        result.addAll(TypesContext.super.getImportResources());
        result.addAll(PreopensContext.super.getImportResources());
        return result;
    }

    @Override
    public void dropDescriptor(int rep) {
        DescriptorEntry entry = descriptors.remove(rep);
        if (entry != null && entry.channel != null) {
            try {
                entry.channel.close();
            } catch (IOException e) {
                // Best-effort close, mirrors WasiIoContext#dropOutputStream.
            }
        }
    }

    @Override
    public void dropDirectoryEntryStream(int rep) {
        dirStreams.remove(rep);
    }

    @Override
    public void dropInputStream(int rep) {
        io.dropInputStream(rep);
    }

    @Override
    public void dropOutputStream(int rep) {
        io.dropOutputStream(rep);
    }

    @Override
    public void dropError(int rep) {
        // Same as WasiIoContext#dropError: no real "error" resource is ever
        // constructed by this implementation.
    }

    private static WitResult okResult(Object value) {
        return WitResult.ok(value);
    }

    private static WitResult errorResult(String errorCode) {
        return WitResult.err(new WitEnum(errorCode));
    }

    private static String descriptorTypeName(int wasiFileType) {
        switch (wasiFileType) {
            case WasiFileType.BLOCK_DEVICE:
                return "block-device";
            case WasiFileType.CHARACTER_DEVICE:
                return "character-device";
            case WasiFileType.DIRECTORY:
                return "directory";
            case WasiFileType.REGULAR_FILE:
                return "regular-file";
            case WasiFileType.SOCKET_DGRAM:
            case WasiFileType.SOCKET_STREAM:
                return "socket";
            case WasiFileType.SYMBOLIC_LINK:
                return "symbolic-link";
            default:
                return "unknown";
        }
    }

    private static Map<String, Object> toDatetime(FileTime time) {
        long nanos = time.to(TimeUnit.NANOSECONDS);
        Map<String, Object> datetime = new LinkedHashMap<>();
        datetime.put("seconds", Math.floorDiv(nanos, 1_000_000_000L));
        datetime.put("nanoseconds", (int) Math.floorMod(nanos, 1_000_000_000L));
        return datetime;
    }

    private static Map<String, Object> buildDescriptorStat(BasicFileAttributes attrs) {
        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("type", new WitEnum(descriptorTypeName(WasiFileType.getWasiFileType(attrs))));
        stat.put("link-count", 1L);
        stat.put("size", attrs.size());
        stat.put("data-access-timestamp", Optional.of(toDatetime(attrs.lastAccessTime())));
        stat.put("data-modification-timestamp", Optional.of(toDatetime(attrs.lastModifiedTime())));
        stat.put("status-change-timestamp", Optional.of(toDatetime(attrs.creationTime())));
        return stat;
    }

    /**
     * Interprets a {@code new-timestamp} variant ({@code no-change}, {@code
     * now}, or {@code timestamp(datetime)}).
     *
     * @return The resolved {@link FileTime}, or empty for {@code no-change}.
     */
    private static Optional<FileTime> resolveTimestamp(WitVariant variant) {
        switch (variant.caseName()) {
            case "now":
                return Optional.of(FileTime.fromMillis(System.currentTimeMillis()));
            case "timestamp": {
                @SuppressWarnings("unchecked")
                Map<String, Object> datetime = (Map<String, Object>) variant.value();
                long seconds = (Long) datetime.get("seconds");
                int nanoseconds = (Integer) datetime.get("nanoseconds");
                return Optional.of(FileTime.from(seconds * 1_000_000_000L + nanoseconds, TimeUnit.NANOSECONDS));
            }
            case "no-change":
            default:
                return Optional.empty();
        }
    }

    @Override
    public WitResult descriptorReadViaStream(WasmtimeComponentInstance instance, WitResource self, long offset) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || entry.directory) {
            return errorResult("is-directory");
        }
        if (!entry.canRead) {
            return errorResult("not-permitted");
        }
        int rep = io.registerInputStream(new FileChannelInputStream(entry.channel, offset));
        return okResult(WitResource.own("input-stream", rep));
    }

    @Override
    public WitResult descriptorWriteViaStream(WasmtimeComponentInstance instance, WitResource self, long offset) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || entry.directory) {
            return errorResult("is-directory");
        }
        if (!entry.canWrite) {
            return errorResult("not-permitted");
        }
        int rep = io.registerOutputStream(new FileChannelOutputStream(entry.channel, offset, false));
        return okResult(WitResource.own("output-stream", rep));
    }

    @Override
    public WitResult descriptorAppendViaStream(WasmtimeComponentInstance instance, WitResource self) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || entry.directory) {
            return errorResult("is-directory");
        }
        if (!entry.canWrite) {
            return errorResult("not-permitted");
        }
        int rep = io.registerOutputStream(new FileChannelOutputStream(entry.channel, 0, true));
        return okResult(WitResource.own("output-stream", rep));
    }

    /**
     * {@code advise} is purely a performance hint (the POSIX {@code
     * posix_fadvise} equivalent) with no {@link FileChannel} counterpart, so
     * this validates the descriptor and otherwise does nothing.
     */
    @Override
    public WitResult descriptorAdvise(WasmtimeComponentInstance instance, WitResource self, long offset, long length,
            WitEnum advice) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null) {
            return errorResult("bad-descriptor");
        }
        return okResult(null);
    }

    /**
     * Data-only variant of {@link #descriptorSync} ({@code fdatasync} vs.
     * {@code fsync}).
     */
    @Override
    public WitResult descriptorSyncData(WasmtimeComponentInstance instance, WitResource self) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null) {
            return errorResult("bad-descriptor");
        }
        if (entry.channel != null) {
            try {
                entry.channel.force(false);
            } catch (IOException e) {
                return errorResult("io");
            }
        }
        return okResult(null);
    }

    @Override
    public WitResult descriptorGetFlags(WasmtimeComponentInstance instance, WitResource self) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null) {
            return errorResult("bad-descriptor");
        }
        Set<String> flags = new LinkedHashSet<>();
        if (entry.canRead) {
            flags.add("read");
        }
        if (entry.canWrite) {
            flags.add("write");
        }
        return okResult(flags);
    }

    @Override
    public WitResult descriptorGetType(WasmtimeComponentInstance instance, WitResource self) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null) {
            return errorResult("bad-descriptor");
        }
        try {
            BasicFileAttributes attrs = Files.readAttributes(entry.path, BasicFileAttributes.class);
            return okResult(new WitEnum(descriptorTypeName(WasiFileType.getWasiFileType(attrs))));
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    @Override
    public WitResult descriptorSetSize(WasmtimeComponentInstance instance, WitResource self, long size) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || entry.directory) {
            return errorResult("is-directory");
        }
        try {
            entry.channel.truncate(size);
            return okResult(null);
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    @Override
    public WitResult descriptorSetTimes(WasmtimeComponentInstance instance, WitResource self,
            WitVariant dataAccessTimestamp, WitVariant dataModificationTimestamp) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null) {
            return errorResult("bad-descriptor");
        }
        try {
            Optional<FileTime> atim = resolveTimestamp(dataAccessTimestamp);
            Optional<FileTime> mtim = resolveTimestamp(dataModificationTimestamp);
            if (atim.isPresent()) {
                Files.setAttribute(entry.path, "lastAccessTime", atim.get());
            }
            if (mtim.isPresent()) {
                Files.setAttribute(entry.path, "lastModifiedTime", mtim.get());
            }
            return okResult(null);
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    /**
     * Positional (pread-style) read directly on the descriptor, independent
     * of any {@code read-via-stream} stream's own cursor.
     */
    @Override
    public WitResult descriptorRead(WasmtimeComponentInstance instance, WitResource self, long length, long offset) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || entry.directory) {
            return errorResult("is-directory");
        }
        if (!entry.canRead) {
            return errorResult("not-permitted");
        }
        try {
            int toRead = (int) Math.min(length, MAX_READ_CHUNK);
            ByteBuffer buffer = ByteBuffer.allocate(toRead);
            int n = entry.channel.read(buffer, offset);
            byte[] data = n > 0 ? Arrays.copyOf(buffer.array(), n) : new byte[0];
            boolean eof = n < 0 || offset + Math.max(n, 0) >= entry.channel.size();
            return okResult(new Object[] { data, eof });
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    /**
     * Positional (pwrite-style) write directly on the descriptor, independent
     * of any {@code write-via-stream}/{@code append-via-stream} stream's own
     * cursor.
     */
    @Override
    public WitResult descriptorWrite(WasmtimeComponentInstance instance, WitResource self, byte[] buffer,
            long offset) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || entry.directory) {
            return errorResult("is-directory");
        }
        if (!entry.canWrite) {
            return errorResult("not-permitted");
        }
        try {
            int n = entry.channel.write(ByteBuffer.wrap(buffer), offset);
            return okResult((long) Math.max(n, 0));
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    @Override
    public WitResult descriptorReadDirectory(WasmtimeComponentInstance instance, WitResource self) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || !entry.directory) {
            return errorResult("not-directory");
        }
        try {
            List<Path> entries = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(entry.path)) {
                for (Path p : stream) {
                    entries.add(p);
                }
            }
            int rep = nextRep.getAndIncrement();
            dirStreams.put(rep, entries.iterator());
            return okResult(WitResource.own(RESOURCE_DIRECTORY_ENTRY_STREAM, rep));
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    @Override
    public WitResult directoryEntryStreamReadDirectoryEntry(WasmtimeComponentInstance instance, WitResource self) {
        Iterator<Path> it = dirStreams.get(self.rep());
        if (it == null) {
            return errorResult("bad-descriptor");
        }
        if (!it.hasNext()) {
            return okResult(Optional.empty());
        }
        Path p = it.next();
        try {
            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", new WitEnum(descriptorTypeName(WasiFileType.getWasiFileType(attrs))));
            entry.put("name", p.getFileName().toString());
            return okResult(Optional.of(entry));
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    @Override
    public WitResult descriptorSync(WasmtimeComponentInstance instance, WitResource self) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null) {
            return errorResult("bad-descriptor");
        }
        if (entry.channel != null) {
            try {
                entry.channel.force(true);
            } catch (IOException e) {
                return errorResult("io");
            }
        }
        return okResult(null);
    }

    @Override
    public WitResult descriptorCreateDirectoryAt(WasmtimeComponentInstance instance, WitResource self, String path) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || !entry.directory) {
            return errorResult("not-directory");
        }
        Path resolved = PathSandbox.resolve(entry.path, path);
        if (resolved == null) {
            return errorResult("access");
        }
        try {
            Files.createDirectory(resolved);
            return okResult(null);
        } catch (FileAlreadyExistsException e) {
            return errorResult("exist");
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    @Override
    public WitResult descriptorStat(WasmtimeComponentInstance instance, WitResource self) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null) {
            return errorResult("bad-descriptor");
        }
        return statPath(entry.path);
    }

    @Override
    public WitResult descriptorStatAt(WasmtimeComponentInstance instance, WitResource self, Set<String> pathFlags,
            String path) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || !entry.directory) {
            return errorResult("not-directory");
        }
        Path resolved = PathSandbox.resolve(entry.path, path);
        if (resolved == null) {
            return errorResult("access");
        }
        return statPath(resolved);
    }

    private static WitResult statPath(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return okResult(buildDescriptorStat(attrs));
        } catch (NoSuchFileException e) {
            return errorResult("no-entry");
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    @Override
    public WitResult descriptorSetTimesAt(WasmtimeComponentInstance instance, WitResource self,
            Set<String> pathFlags, String path, WitVariant dataAccessTimestamp, WitVariant dataModificationTimestamp) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || !entry.directory) {
            return errorResult("not-directory");
        }
        Path resolved = PathSandbox.resolve(entry.path, path);
        if (resolved == null) {
            return errorResult("access");
        }
        try {
            Optional<FileTime> atim = resolveTimestamp(dataAccessTimestamp);
            Optional<FileTime> mtim = resolveTimestamp(dataModificationTimestamp);
            if (atim.isPresent()) {
                Files.setAttribute(resolved, "lastAccessTime", atim.get());
            }
            if (mtim.isPresent()) {
                Files.setAttribute(resolved, "lastModifiedTime", mtim.get());
            }
            return okResult(null);
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    @Override
    public WitResult descriptorLinkAt(WasmtimeComponentInstance instance, WitResource self, Set<String> oldPathFlags,
            String oldPath, WitResource newDescriptor, String newPath) {
        DescriptorEntry oldEntry = descriptors.get(self.rep());
        DescriptorEntry newEntry = descriptors.get(newDescriptor.rep());
        if (oldEntry == null || newEntry == null || !oldEntry.directory || !newEntry.directory) {
            return errorResult("not-directory");
        }
        Path oldPathResolved = PathSandbox.resolve(oldEntry.path, oldPath);
        Path newPathResolved = PathSandbox.resolve(newEntry.path, newPath);
        if (oldPathResolved == null || newPathResolved == null) {
            return errorResult("access");
        }
        try {
            Files.createLink(newPathResolved, oldPathResolved);
            return okResult(null);
        } catch (FileAlreadyExistsException e) {
            return errorResult("exist");
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    @Override
    public WitResult descriptorOpenAt(WasmtimeComponentInstance instance, WitResource self, Set<String> pathFlags,
            String path, Set<String> openFlags, Set<String> flags) {
        DescriptorEntry base = descriptors.get(self.rep());
        if (base == null || !base.directory) {
            return errorResult("not-directory");
        }
        Path resolved = PathSandbox.resolve(base.path, path);
        if (resolved == null) {
            return errorResult("access");
        }

        boolean wantDirectory = openFlags.contains("directory");
        boolean create = openFlags.contains("create");
        boolean exclusive = openFlags.contains("exclusive");
        boolean truncate = openFlags.contains("truncate");
        boolean canRead = flags.contains("read");
        boolean canWrite = flags.contains("write");

        try {
            boolean existingDirectory = Files.isDirectory(resolved);
            if (wantDirectory || existingDirectory) {
                if (!existingDirectory) {
                    return errorResult("not-directory");
                }
                int rep = nextRep.getAndIncrement();
                descriptors.put(rep, new DescriptorEntry(resolved, true, null, true, true));
                return okResult(WitResource.own(RESOURCE_DESCRIPTOR, rep));
            }

            Set<OpenOption> options = new HashSet<>();
            if (canWrite) {
                options.add(StandardOpenOption.WRITE);
            }
            if (create) {
                options.add(StandardOpenOption.CREATE);
            }
            if (exclusive) {
                options.add(StandardOpenOption.CREATE_NEW);
            }
            if (truncate) {
                options.add(StandardOpenOption.TRUNCATE_EXISTING);
            }
            if (canRead || options.isEmpty()) {
                options.add(StandardOpenOption.READ);
            }

            FileChannel channel = FileChannel.open(resolved, options);
            int rep = nextRep.getAndIncrement();
            descriptors.put(rep, new DescriptorEntry(resolved, false, channel, canRead, canWrite));
            return okResult(WitResource.own(RESOURCE_DESCRIPTOR, rep));
        } catch (FileAlreadyExistsException e) {
            return errorResult("exist");
        } catch (NoSuchFileException e) {
            return errorResult("no-entry");
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    /**
     * Symlinks are not supported (see class javadoc).
     */
    @Override
    public WitResult descriptorReadlinkAt(WasmtimeComponentInstance instance, WitResource self, String path) {
        return errorResult("unsupported");
    }

    @Override
    public WitResult descriptorRemoveDirectoryAt(WasmtimeComponentInstance instance, WitResource self, String path) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || !entry.directory) {
            return errorResult("not-directory");
        }
        Path resolved = PathSandbox.resolve(entry.path, path);
        if (resolved == null) {
            return errorResult("access");
        }
        try {
            if (!Files.exists(resolved)) {
                return errorResult("no-entry");
            }
            if (!Files.isDirectory(resolved)) {
                return errorResult("not-directory");
            }
            Files.delete(resolved);
            return okResult(null);
        } catch (NoSuchFileException e) {
            return errorResult("no-entry");
        } catch (DirectoryNotEmptyException e) {
            return errorResult("not-empty");
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    @Override
    public WitResult descriptorRenameAt(WasmtimeComponentInstance instance, WitResource self, String oldPath,
            WitResource newDescriptor, String newPath) {
        DescriptorEntry oldEntry = descriptors.get(self.rep());
        DescriptorEntry newEntry = descriptors.get(newDescriptor.rep());
        if (oldEntry == null || newEntry == null || !oldEntry.directory || !newEntry.directory) {
            return errorResult("not-directory");
        }
        Path oldPathResolved = PathSandbox.resolve(oldEntry.path, oldPath);
        Path newPathResolved = PathSandbox.resolve(newEntry.path, newPath);
        if (oldPathResolved == null || newPathResolved == null) {
            return errorResult("access");
        }
        try {
            Files.move(oldPathResolved, newPathResolved, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return okResult(null);
        } catch (NoSuchFileException e) {
            return errorResult("no-entry");
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    /**
     * Symlinks are not supported (see class javadoc).
     */
    @Override
    public WitResult descriptorSymlinkAt(WasmtimeComponentInstance instance, WitResource self, String oldPath,
            String newPath) {
        return errorResult("unsupported");
    }

    @Override
    public WitResult descriptorUnlinkFileAt(WasmtimeComponentInstance instance, WitResource self, String path) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || !entry.directory) {
            return errorResult("not-directory");
        }
        Path resolved = PathSandbox.resolve(entry.path, path);
        if (resolved == null) {
            return errorResult("access");
        }
        try {
            if (Files.isDirectory(resolved)) {
                return errorResult("is-directory");
            }
            Files.delete(resolved);
            return okResult(null);
        } catch (NoSuchFileException e) {
            return errorResult("no-entry");
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    /**
     * Compares resolved host paths (falling back to {@link Files#isSameFile}
     * when available) since this implementation doesn't track a separate
     * inode/file-key identity of its own.
     */
    @Override
    public boolean descriptorIsSameObject(WasmtimeComponentInstance instance, WitResource self, WitResource other) {
        DescriptorEntry a = descriptors.get(self.rep());
        DescriptorEntry b = descriptors.get(other.rep());
        if (a == null || b == null) {
            return false;
        }
        try {
            return Files.isSameFile(a.path, b.path);
        } catch (IOException e) {
            return a.path.toAbsolutePath().normalize().equals(b.path.toAbsolutePath().normalize());
        }
    }

    @Override
    public WitResult descriptorMetadataHash(WasmtimeComponentInstance instance, WitResource self) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null) {
            return errorResult("bad-descriptor");
        }
        return metadataHashFor(entry.path);
    }

    @Override
    public WitResult descriptorMetadataHashAt(WasmtimeComponentInstance instance, WitResource self,
            Set<String> pathFlags, String path) {
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || !entry.directory) {
            return errorResult("not-directory");
        }
        Path resolved = PathSandbox.resolve(entry.path, path);
        if (resolved == null) {
            return errorResult("access");
        }
        return metadataHashFor(resolved);
    }

    private static WitResult metadataHashFor(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            long lower = attrs.fileKey() != null ? attrs.fileKey().hashCode() : path.toAbsolutePath().hashCode();
            long upper = attrs.size() ^ attrs.lastModifiedTime().toMillis();
            Map<String, Object> hash = new LinkedHashMap<>();
            hash.put("lower", lower);
            hash.put("upper", upper);
            return okResult(hash);
        } catch (NoSuchFileException e) {
            return errorResult("no-entry");
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    /**
     * This implementation never attaches real error-code metadata to
     * {@code error} resources (see class javadoc), so there's never a
     * filesystem-related code to report.
     */
    @Override
    public Optional<Object> typesFilesystemErrorCode(WasmtimeComponentInstance instance, WitResource err) {
        return Optional.empty();
    }

    @Override
    public List<Object> preopensGetDirectories(WasmtimeComponentInstance instance) {
        List<Object> result = new ArrayList<>();
        for (Map.Entry<String, Path> e : pathMappings.entrySet()) {
            int rep = nextRep.getAndIncrement();
            descriptors.put(rep, new DescriptorEntry(e.getValue(), true, null, true, true));
            result.add(new Object[] { WitResource.own(RESOURCE_DESCRIPTOR, rep), e.getKey() });
        }
        return result;
    }

    @Override
    public WasiFilesystemContext withVersion(SemanticVersion version) {
        if (!supportsVersion(version)) {
            throw new IllegalArgumentException("Unsupported version: " + version);
        }
        this.version = version;
        return this;
    }

    @Override
    public SemanticVersion getVersion() {
        return this.version;
    }

    @Override
    public SemanticVersion getMiniumVersion() {
        return new SemanticVersion(0, 0, 1);
    }

    @Override
    public SemanticVersion getMaximumVersion() {
        return new SemanticVersion(0, 3, 0);
    }
}
