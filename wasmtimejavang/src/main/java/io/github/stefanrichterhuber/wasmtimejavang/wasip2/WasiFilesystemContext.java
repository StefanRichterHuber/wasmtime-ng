package io.github.stefanrichterhuber.wasmtimejavang.wasip2;

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
import io.github.stefanrichterhuber.wasmtimejavang.ComponentFunction;
import io.github.stefanrichterhuber.wasmtimejavang.ResourceDestructor;
import io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitEnum;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;
import io.github.stefanrichterhuber.wasmtimejavang.internal.PathSandbox;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.WasiFileType;

/**
 * Implementation of {@code wasi:filesystem/types} and
 * {@code wasi:filesystem/preopens} (WASI Preview 2, 0.2.6) -- the
 * {@code "wasi-filesystem"} component context.
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
 * default), and there is no {@code symlink-at}/{@code readlink-at} -- WASI
 * components don't require them to run, and sandboxed filesystems like
 * JimFS have inconsistent symlink support, so this is documented as
 * unsupported rather than implemented on uncertain footing.
 */
public class WasiFilesystemContext implements WasmComponentContext {

    /** The stable name other contexts reference via {@code getDependencies()}. */
    public static final String NAME = "wasi-filesystem";

    private static final String WASI_FILESYSTEM_TYPES = "wasi:filesystem/types";
    private static final String WASI_FILESYSTEM_PREOPENS = "wasi:filesystem/preopens";
    private static final Set<String> PROVIDED_INTERFACES = Set.of(WASI_FILESYSTEM_TYPES, WASI_FILESYSTEM_PREOPENS);

    private static final String RESOURCE_DESCRIPTOR = "descriptor";
    private static final String RESOURCE_DIRECTORY_ENTRY_STREAM = "directory-entry-stream";

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
        return PROVIDED_INTERFACES;
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
        String types = WASI_FILESYSTEM_TYPES + "@" + version;
        String preopens = WASI_FILESYSTEM_PREOPENS + "@" + version;

        result.add(func(types, "[method]descriptor.read-via-stream", this::readViaStream));
        result.add(func(types, "[method]descriptor.write-via-stream", this::writeViaStream));
        result.add(func(types, "[method]descriptor.append-via-stream", this::appendViaStream));
        result.add(func(types, "[method]descriptor.get-flags", this::getFlags));
        result.add(func(types, "[method]descriptor.set-size", this::setSize));
        result.add(func(types, "[method]descriptor.set-times", this::setTimes));
        result.add(func(types, "[method]descriptor.read-directory", this::readDirectory));
        result.add(func(types, "[method]descriptor.sync", this::sync));
        result.add(func(types, "[method]descriptor.create-directory-at", this::createDirectoryAt));
        result.add(func(types, "[method]descriptor.stat", this::stat));
        result.add(func(types, "[method]descriptor.stat-at", this::statAt));
        result.add(func(types, "[method]descriptor.link-at", this::linkAt));
        result.add(func(types, "[method]descriptor.open-at", this::openAt));
        result.add(func(types, "[method]descriptor.remove-directory-at", this::removeDirectoryAt));
        result.add(func(types, "[method]descriptor.rename-at", this::renameAt));
        result.add(func(types, "[method]descriptor.unlink-file-at", this::unlinkFileAt));
        result.add(func(types, "[method]descriptor.metadata-hash", this::metadataHash));
        result.add(func(types, "[method]descriptor.metadata-hash-at", this::metadataHashAt));
        result.add(func(types, "[method]directory-entry-stream.read-directory-entry", this::readDirectoryEntry));

        result.add(func(preopens, "get-directories", this::getDirectories));
        return result;
    }

    @Override
    public List<ComponentImportResource> getImportResources() {
        String types = WASI_FILESYSTEM_TYPES + "@" + version;
        String preopens = WASI_FILESYSTEM_PREOPENS + "@" + version;
        return List.of(
                resource(types, RESOURCE_DESCRIPTOR, this::dropDescriptor),
                resource(types, RESOURCE_DIRECTORY_ENTRY_STREAM, this::dropDirectoryStream),
                resource(types, "input-stream", io::dropInputStream),
                resource(types, "output-stream", io::dropOutputStream),
                resource(preopens, RESOURCE_DESCRIPTOR, this::dropDescriptor));
    }

    private static ComponentImportFunction func(String interfaceName, String funcName, ComponentFunction function) {
        return new ComponentImportFunction(interfaceName, funcName, function);
    }

    private static ComponentImportResource resource(String interfaceName, String resourceName,
            ResourceDestructor destructor) {
        return new ComponentImportResource(interfaceName, resourceName, destructor);
    }

    private void dropDescriptor(int rep) {
        DescriptorEntry entry = descriptors.remove(rep);
        if (entry != null && entry.channel != null) {
            try {
                entry.channel.close();
            } catch (IOException e) {
                // Best-effort close, mirrors WasiIoContext#dropOutputStream.
            }
        }
    }

    private void dropDirectoryStream(int rep) {
        dirStreams.remove(rep);
    }

    private static Object[] okResult(Object value) {
        return new Object[] { WitResult.ok(value) };
    }

    private static Object[] errorResult(String errorCode) {
        return new Object[] { WitResult.err(new WitEnum(errorCode)) };
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

    @SuppressWarnings("unchecked")
    private static Set<String> flagsOf(Object arg) {
        return (Set<String>) arg;
    }

    protected Object[] readViaStream(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        long offset = (Long) args[1];
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

    protected Object[] writeViaStream(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        long offset = (Long) args[1];
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

    protected Object[] appendViaStream(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
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

    protected Object[] getFlags(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
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

    protected Object[] setSize(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        long size = (Long) args[1];
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

    protected Object[] setTimes(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        WitVariant accessTimestamp = (WitVariant) args[1];
        WitVariant modificationTimestamp = (WitVariant) args[2];
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null) {
            return errorResult("bad-descriptor");
        }
        try {
            Optional<FileTime> atim = resolveTimestamp(accessTimestamp);
            Optional<FileTime> mtim = resolveTimestamp(modificationTimestamp);
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

    protected Object[] readDirectory(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
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

    protected Object[] readDirectoryEntry(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
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

    protected Object[] sync(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
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

    protected Object[] createDirectoryAt(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        String pathStr = (String) args[1];
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || !entry.directory) {
            return errorResult("not-directory");
        }
        Path resolved = PathSandbox.resolve(entry.path, pathStr);
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

    protected Object[] stat(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null) {
            return errorResult("bad-descriptor");
        }
        return statPath(entry.path);
    }

    protected Object[] statAt(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        String pathStr = (String) args[2];
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || !entry.directory) {
            return errorResult("not-directory");
        }
        Path resolved = PathSandbox.resolve(entry.path, pathStr);
        if (resolved == null) {
            return errorResult("access");
        }
        return statPath(resolved);
    }

    private static Object[] statPath(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return okResult(buildDescriptorStat(attrs));
        } catch (NoSuchFileException e) {
            return errorResult("no-entry");
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    protected Object[] linkAt(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        String oldPathStr = (String) args[2];
        WitResource newDescriptor = (WitResource) args[3];
        String newPathStr = (String) args[4];

        DescriptorEntry oldEntry = descriptors.get(self.rep());
        DescriptorEntry newEntry = descriptors.get(newDescriptor.rep());
        if (oldEntry == null || newEntry == null || !oldEntry.directory || !newEntry.directory) {
            return errorResult("not-directory");
        }
        Path oldPath = PathSandbox.resolve(oldEntry.path, oldPathStr);
        Path newPath = PathSandbox.resolve(newEntry.path, newPathStr);
        if (oldPath == null || newPath == null) {
            return errorResult("access");
        }
        try {
            Files.createLink(newPath, oldPath);
            return okResult(null);
        } catch (FileAlreadyExistsException e) {
            return errorResult("exist");
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    protected Object[] openAt(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        String pathStr = (String) args[2];
        Set<String> openFlags = flagsOf(args[3]);
        Set<String> descriptorFlags = flagsOf(args[4]);

        DescriptorEntry base = descriptors.get(self.rep());
        if (base == null || !base.directory) {
            return errorResult("not-directory");
        }
        Path resolved = PathSandbox.resolve(base.path, pathStr);
        if (resolved == null) {
            return errorResult("access");
        }

        boolean wantDirectory = openFlags.contains("directory");
        boolean create = openFlags.contains("create");
        boolean exclusive = openFlags.contains("exclusive");
        boolean truncate = openFlags.contains("truncate");
        boolean canRead = descriptorFlags.contains("read");
        boolean canWrite = descriptorFlags.contains("write");

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

    protected Object[] removeDirectoryAt(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        String pathStr = (String) args[1];
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || !entry.directory) {
            return errorResult("not-directory");
        }
        Path resolved = PathSandbox.resolve(entry.path, pathStr);
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

    protected Object[] renameAt(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        String oldPathStr = (String) args[1];
        WitResource newDescriptor = (WitResource) args[2];
        String newPathStr = (String) args[3];

        DescriptorEntry oldEntry = descriptors.get(self.rep());
        DescriptorEntry newEntry = descriptors.get(newDescriptor.rep());
        if (oldEntry == null || newEntry == null || !oldEntry.directory || !newEntry.directory) {
            return errorResult("not-directory");
        }
        Path oldPath = PathSandbox.resolve(oldEntry.path, oldPathStr);
        Path newPath = PathSandbox.resolve(newEntry.path, newPathStr);
        if (oldPath == null || newPath == null) {
            return errorResult("access");
        }
        try {
            Files.move(oldPath, newPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return okResult(null);
        } catch (NoSuchFileException e) {
            return errorResult("no-entry");
        } catch (IOException e) {
            return errorResult("io");
        }
    }

    protected Object[] unlinkFileAt(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        String pathStr = (String) args[1];
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || !entry.directory) {
            return errorResult("not-directory");
        }
        Path resolved = PathSandbox.resolve(entry.path, pathStr);
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

    protected Object[] metadataHash(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null) {
            return errorResult("bad-descriptor");
        }
        return metadataHashFor(entry.path);
    }

    protected Object[] metadataHashAt(WasmtimeComponentInstance instance, Object[] args) {
        WitResource self = (WitResource) args[0];
        String pathStr = (String) args[2];
        DescriptorEntry entry = descriptors.get(self.rep());
        if (entry == null || !entry.directory) {
            return errorResult("not-directory");
        }
        Path resolved = PathSandbox.resolve(entry.path, pathStr);
        if (resolved == null) {
            return errorResult("access");
        }
        return metadataHashFor(resolved);
    }

    private static Object[] metadataHashFor(Path path) {
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

    protected Object[] getDirectories(WasmtimeComponentInstance instance, Object[] args) {
        List<Object> result = new ArrayList<>();
        for (Map.Entry<String, Path> e : pathMappings.entrySet()) {
            int rep = nextRep.getAndIncrement();
            descriptors.put(rep, new DescriptorEntry(e.getValue(), true, null, true, true));
            result.add(new Object[] { WitResource.own(RESOURCE_DESCRIPTOR, rep), e.getKey() });
        }
        return new Object[] { result };
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
