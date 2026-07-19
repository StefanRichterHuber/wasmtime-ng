package io.github.stefanrichterhuber.wasmtimejavang.wasip2;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext.ComponentImportFunction;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext.ComponentImportResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitEnum;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;

/**
 * Direct unit tests for {@link WasiFilesystemContext}, wiring its {@code
 * "wasi-io"} dependency by hand (a real {@link WasiIoContext}) and backing
 * every preopened directory with a sandboxed {@link Jimfs} filesystem
 * (never the host filesystem) -- covering error paths and functions (e.g.
 * {@code metadata-hash}, {@code link-at}, flag/permission checks,
 * directory-entry-stream exhaustion) the end-to-end {@code
 * WasmtimeWasiP2Test#wasip2filetest} fixture doesn't happen to exercise.
 */
public class WasiFilesystemContextTest {

    private FileSystem fs;

    @BeforeEach
    public void setUp() throws Exception {
        fs = Jimfs.newFileSystem(Configuration.unix().toBuilder()
                .setAttributeViews("basic", "owner", "posix", "unix").build());
    }

    @AfterEach
    public void tearDown() throws Exception {
        fs.close();
    }

    private static WasiFilesystemContext newLinkedFilesystem(WasiIoContext io) {
        WasiFilesystemContext filesystem = new WasiFilesystemContext();
        filesystem.onDependenciesResolved(
                (name, version) -> WasiIoContext.NAME.equals(name) ? Optional.of(io) : Optional.empty());
        return filesystem;
    }

    private static WitResource resourceOf(int rep) {
        return new WitResource(null, rep, true);
    }

    /** Preopens {@code dir} under {@code clientName} and returns its descriptor. */
    @SuppressWarnings("unchecked")
    private static WitResource preopen(WasiFilesystemContext filesystem, Path dir, String clientName) {
        filesystem.withDirectory(dir, clientName);
        Object[] result = filesystem.getDirectories(null, new Object[0]);
        List<Object> tuples = (List<Object>) result[0];
        for (Object t : tuples) {
            Object[] tuple = (Object[]) t;
            if (clientName.equals(tuple[1])) {
                return (WitResource) tuple[0];
            }
        }
        throw new AssertionError("preopen \"" + clientName + "\" not found");
    }

    private static WitResult openAt(WasiFilesystemContext filesystem, WitResource dir, String path,
            Set<String> openFlags, Set<String> descriptorFlags) {
        Object[] result = filesystem.openAt(null,
                new Object[] { dir, Set.of("symlink-follow"), path, openFlags, descriptorFlags });
        return (WitResult) result[0];
    }

    private static WitResource openFile(WasiFilesystemContext filesystem, WitResource dir, String path,
            boolean create, boolean write) {
        Set<String> openFlags = create ? Set.of("create") : Set.of();
        Set<String> descriptorFlags = write ? Set.of("read", "write") : Set.of("read");
        WitResult result = openAt(filesystem, dir, path, openFlags, descriptorFlags);
        assertTrue(result.ok(), "open-at failed: " + result.value());
        return (WitResource) result.value();
    }

    @Test
    public void nameProvidedInterfacesAndDependencies() {
        WasiFilesystemContext filesystem = new WasiFilesystemContext();
        assertEquals("wasi-filesystem", filesystem.name());
        assertEquals(WasiFilesystemContext.NAME, filesystem.name());
        assertEquals(Set.of("wasi:filesystem/types", "wasi:filesystem/preopens"), filesystem.getProvidedInterfaces());
        assertEquals(List.of(WasiIoContext.NAME), filesystem.getDependencies());
    }

    @Test
    public void implementsWasmComponentContext() {
        assertTrue(WasmComponentContext.class.isAssignableFrom(WasiFilesystemContext.class));
    }

    @Test
    public void onDependenciesResolvedThrowsWhenWasiIoIsMissing() {
        WasiFilesystemContext filesystem = new WasiFilesystemContext();
        ComponentContextLookup emptyLookup = (name, version) -> Optional.empty();
        assertThrows(IllegalStateException.class, () -> filesystem.onDependenciesResolved(emptyLookup));
    }

    @Test
    public void withDirectoryReturnsThisForChaining() {
        WasiFilesystemContext filesystem = new WasiFilesystemContext();
        assertSame(filesystem, filesystem.withDirectory(fs.getPath("/"), "."));
    }

    @Test
    public void importFunctionsCoverEveryDeclaredMethod() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        List<ComponentImportFunction> functions = filesystem.getImportFunctions();
        String types = "wasi:filesystem/types@" + filesystem.getVersion();
        String preopens = "wasi:filesystem/preopens@" + filesystem.getVersion();

        List<String> expectedTypeFuncs = List.of(
                "[method]descriptor.read-via-stream", "[method]descriptor.write-via-stream",
                "[method]descriptor.append-via-stream", "[method]descriptor.get-flags",
                "[method]descriptor.set-size", "[method]descriptor.set-times",
                "[method]descriptor.read-directory", "[method]descriptor.sync",
                "[method]descriptor.create-directory-at", "[method]descriptor.stat",
                "[method]descriptor.stat-at", "[method]descriptor.link-at",
                "[method]descriptor.open-at", "[method]descriptor.remove-directory-at",
                "[method]descriptor.rename-at", "[method]descriptor.unlink-file-at",
                "[method]descriptor.metadata-hash", "[method]descriptor.metadata-hash-at",
                "[method]directory-entry-stream.read-directory-entry");
        for (String funcName : expectedTypeFuncs) {
            assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals(types) && f.funcName().equals(funcName)),
                    "missing " + funcName);
        }
        assertTrue(functions.stream()
                .anyMatch(f -> f.interfaceName().equals(preopens) && f.funcName().equals("get-directories")));
    }

    @Test
    public void importResourcesCoverEveryDeclaredResourceAndDestructorsWork() {
        WasiIoContext io = new WasiIoContext();
        WasiFilesystemContext filesystem = newLinkedFilesystem(io);
        List<ComponentImportResource> resources = filesystem.getImportResources();
        String types = "wasi:filesystem/types@" + filesystem.getVersion();
        String preopens = "wasi:filesystem/preopens@" + filesystem.getVersion();

        assertTrue(resources.stream()
                .anyMatch(r -> r.interfaceName().equals(types) && r.resourceName().equals("descriptor")));
        assertTrue(resources.stream().anyMatch(
                r -> r.interfaceName().equals(types) && r.resourceName().equals("directory-entry-stream")));
        assertTrue(resources.stream()
                .anyMatch(r -> r.interfaceName().equals(types) && r.resourceName().equals("input-stream")));
        assertTrue(resources.stream()
                .anyMatch(r -> r.interfaceName().equals(types) && r.resourceName().equals("output-stream")));
        assertTrue(resources.stream()
                .anyMatch(r -> r.interfaceName().equals(preopens) && r.resourceName().equals("descriptor")));

        // input-stream/output-stream destructors delegate to the shared "wasi-io" table.
        ComponentImportResource inputStreamResource = resources.stream()
                .filter(r -> r.interfaceName().equals(types) && r.resourceName().equals("input-stream")).findFirst()
                .orElseThrow();
        int inRep = io.registerInputStream(new java.io.ByteArrayInputStream(new byte[0]));
        inputStreamResource.destructor().drop(inRep);
        assertNotNull(io); // sanity: io itself still usable
        assertEquals(null, io.getInputStream(inRep));

        // Dropping an unknown descriptor/directory-entry-stream rep must not throw.
        ComponentImportResource descriptorResource = resources.stream()
                .filter(r -> r.interfaceName().equals(types) && r.resourceName().equals("descriptor")).findFirst()
                .orElseThrow();
        assertDoesNotThrow(() -> descriptorResource.destructor().drop(999));
        ComponentImportResource dirStreamResource = resources.stream()
                .filter(r -> r.interfaceName().equals(types) && r.resourceName().equals("directory-entry-stream"))
                .findFirst().orElseThrow();
        assertDoesNotThrow(() -> dirStreamResource.destructor().drop(999));
    }

    @Test
    public void getDirectoriesReturnsConfiguredPreopens() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        WitResource root = preopen(filesystem, fs.getPath("/"), ".");
        assertEquals("descriptor", root.resourceName());
        assertTrue(root.owned());
    }

    @Test
    public void openAtOpensExistingFileForReadingAndReadViaStreamWorks() throws Exception {
        WasiIoContext io = new WasiIoContext();
        WasiFilesystemContext filesystem = newLinkedFilesystem(io);
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("input.txt"), "hello");
        WitResource dir = preopen(filesystem, root, ".");

        WitResource file = openFile(filesystem, dir, "input.txt", false, false);

        Object[] streamResult = filesystem.readViaStream(null, new Object[] { file, 0L });
        WitResult wr = (WitResult) streamResult[0];
        assertTrue(wr.ok());
        WitResource inputStream = (WitResource) wr.value();
        assertEquals("input-stream", inputStream.resourceName());
        assertEquals("hello", new String(io.getInputStream(inputStream.rep()).readAllBytes(), "UTF-8"));
    }

    @Test
    public void openAtCreateAndWriteViaStreamWritesFile() throws Exception {
        WasiIoContext io = new WasiIoContext();
        WasiFilesystemContext filesystem = newLinkedFilesystem(io);
        Path root = fs.getPath("/");
        WitResource dir = preopen(filesystem, root, ".");

        WitResource file = openFile(filesystem, dir, "new.txt", true, true);
        Object[] streamResult = filesystem.writeViaStream(null, new Object[] { file, 0L });
        WitResult wr = (WitResult) streamResult[0];
        assertTrue(wr.ok());
        WitResource outputStream = (WitResource) wr.value();
        io.getOutputStream(outputStream.rep()).write("written via stream".getBytes("UTF-8"));

        assertEquals("written via stream", Files.readString(root.resolve("new.txt")));
    }

    @Test
    public void appendViaStreamAlwaysWritesAtCurrentEnd() throws Exception {
        WasiIoContext io = new WasiIoContext();
        WasiFilesystemContext filesystem = newLinkedFilesystem(io);
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("append.txt"), "first-");
        WitResource dir = preopen(filesystem, root, ".");
        WitResource file = openFile(filesystem, dir, "append.txt", false, true);

        Object[] streamResult = filesystem.appendViaStream(null, new Object[] { file });
        WitResult wr = (WitResult) streamResult[0];
        assertTrue(wr.ok());
        WitResource outputStream = (WitResource) wr.value();
        io.getOutputStream(outputStream.rep()).write("second".getBytes("UTF-8"));

        assertEquals("first-second", Files.readString(root.resolve("append.txt")));
    }

    @Test
    public void readViaStreamOnDirectoryFails() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        WitResource dir = preopen(filesystem, fs.getPath("/"), ".");
        Object[] result = filesystem.readViaStream(null, new Object[] { dir, 0L });
        WitResult wr = (WitResult) result[0];
        assertFalse(wr.ok());
        assertEquals(new WitEnum("is-directory"), wr.value());
    }

    @Test
    public void readViaStreamWithoutReadPermissionFails() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("f.txt"), "x");
        WitResource dir = preopen(filesystem, root, ".");
        WitResult opened = openAt(filesystem, dir, "f.txt", Set.of(), Set.of("write"));
        assertTrue(opened.ok());
        WitResource file = (WitResource) opened.value();

        Object[] result = filesystem.readViaStream(null, new Object[] { file, 0L });
        WitResult wr = (WitResult) result[0];
        assertFalse(wr.ok());
        assertEquals(new WitEnum("not-permitted"), wr.value());
    }

    @Test
    public void writeViaStreamWithoutWritePermissionFails() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("f.txt"), "x");
        WitResource dir = preopen(filesystem, root, ".");
        WitResource file = openFile(filesystem, dir, "f.txt", false, false);

        Object[] result = filesystem.writeViaStream(null, new Object[] { file, 0L });
        WitResult wr = (WitResult) result[0];
        assertFalse(wr.ok());
        assertEquals(new WitEnum("not-permitted"), wr.value());
    }

    @Test
    public void getFlagsReturnsReadWriteSet() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        WitResource dir = preopen(filesystem, root, ".");
        WitResource file = openFile(filesystem, dir, "rw.txt", true, true);

        Object[] result = filesystem.getFlags(null, new Object[] { file });
        WitResult wr = (WitResult) result[0];
        assertTrue(wr.ok());
        @SuppressWarnings("unchecked")
        Set<String> flags = (Set<String>) wr.value();
        assertEquals(Set.of("read", "write"), flags);
    }

    @Test
    public void getFlagsOnUnknownDescriptorFails() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Object[] result = filesystem.getFlags(null, new Object[] { resourceOf(999) });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("bad-descriptor"), ((WitResult) result[0]).value());
    }

    @Test
    public void setSizeTruncatesFile() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("t.txt"), "0123456789");
        WitResource dir = preopen(filesystem, root, ".");
        WitResource file = openFile(filesystem, dir, "t.txt", false, true);

        Object[] result = filesystem.setSize(null, new Object[] { file, 4L });
        assertTrue(((WitResult) result[0]).ok());
        assertEquals(4, Files.size(root.resolve("t.txt")));
    }

    @Test
    public void setSizeOnDirectoryFails() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        WitResource dir = preopen(filesystem, fs.getPath("/"), ".");
        Object[] result = filesystem.setSize(null, new Object[] { dir, 0L });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("is-directory"), ((WitResult) result[0]).value());
    }

    @Test
    public void setTimesNowUpdatesModificationTime() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Path target = root.resolve("times.txt");
        Files.writeString(target, "x");
        Files.setAttribute(target, "lastModifiedTime", FileTime.fromMillis(0));
        WitResource dir = preopen(filesystem, root, ".");
        WitResource file = openFile(filesystem, dir, "times.txt", false, true);

        Object[] result = filesystem.setTimes(null,
                new Object[] { file, new WitVariant("no-change", null), new WitVariant("now", null) });
        assertTrue(((WitResult) result[0]).ok());
        assertTrue(Files.getLastModifiedTime(target).toMillis() > 0);
    }

    @Test
    public void setTimesTimestampSetsExactTime() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Path target = root.resolve("times2.txt");
        Files.writeString(target, "x");
        WitResource dir = preopen(filesystem, root, ".");
        WitResource file = openFile(filesystem, dir, "times2.txt", false, true);

        Map<String, Object> datetime = Map.of("seconds", 1_000_000L, "nanoseconds", 0);
        Object[] result = filesystem.setTimes(null,
                new Object[] { file, new WitVariant("timestamp", datetime), new WitVariant("no-change", null) });
        assertTrue(((WitResult) result[0]).ok());
        assertEquals(1_000_000L, Files.getAttribute(target, "lastAccessTime") instanceof FileTime ft
                ? ft.to(TimeUnit.SECONDS)
                : -1);
    }

    @Test
    public void setTimesOnUnknownDescriptorFails() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Object[] result = filesystem.setTimes(null,
                new Object[] { resourceOf(999), new WitVariant("no-change", null), new WitVariant("no-change", null) });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("bad-descriptor"), ((WitResult) result[0]).value());
    }

    @Test
    public void readDirectoryAndReadDirectoryEntryEnumeratesEntries() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("a.txt"), "a");
        Files.writeString(root.resolve("b.txt"), "b");
        WitResource dir = preopen(filesystem, root, ".");

        Object[] rdResult = filesystem.readDirectory(null, new Object[] { dir });
        WitResult rdWr = (WitResult) rdResult[0];
        assertTrue(rdWr.ok());
        WitResource dirStream = (WitResource) rdWr.value();
        assertEquals("directory-entry-stream", dirStream.resourceName());

        // Root may contain more than a.txt/b.txt (e.g. Jimfs's default "/work"
        // directory), so only assert on what this test itself created.
        Map<String, Object> byName = new java.util.HashMap<>();
        while (true) {
            Object[] entryResult = filesystem.readDirectoryEntry(null, new Object[] { dirStream });
            WitResult entryWr = (WitResult) entryResult[0];
            assertTrue(entryWr.ok());
            @SuppressWarnings("unchecked")
            Optional<Map<String, Object>> entry = (Optional<Map<String, Object>>) entryWr.value();
            if (entry.isEmpty()) {
                break;
            }
            Map<String, Object> e = entry.get();
            byName.put((String) e.get("name"), e.get("type"));
        }
        assertEquals(new WitEnum("regular-file"), byName.get("a.txt"));
        assertEquals(new WitEnum("regular-file"), byName.get("b.txt"));
    }

    @Test
    public void readDirectoryOnNonDirectoryFails() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("f.txt"), "x");
        WitResource dir = preopen(filesystem, root, ".");
        WitResource file = openFile(filesystem, dir, "f.txt", false, false);

        Object[] result = filesystem.readDirectory(null, new Object[] { file });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("not-directory"), ((WitResult) result[0]).value());
    }

    @Test
    public void readDirectoryEntryOnUnknownStreamFails() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Object[] result = filesystem.readDirectoryEntry(null, new Object[] { resourceOf(999) });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("bad-descriptor"), ((WitResult) result[0]).value());
    }

    @Test
    public void syncOnFileAndDirectorySucceeds() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        WitResource dir = preopen(filesystem, root, ".");
        WitResource file = openFile(filesystem, dir, "s.txt", true, true);

        assertTrue(((WitResult) filesystem.sync(null, new Object[] { dir })[0]).ok());
        assertTrue(((WitResult) filesystem.sync(null, new Object[] { file })[0]).ok());
    }

    @Test
    public void syncOnUnknownDescriptorFails() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Object[] result = filesystem.sync(null, new Object[] { resourceOf(999) });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("bad-descriptor"), ((WitResult) result[0]).value());
    }

    @Test
    public void createDirectoryAtCreatesDirectory() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        WitResource dir = preopen(filesystem, root, ".");

        Object[] result = filesystem.createDirectoryAt(null, new Object[] { dir, "subdir" });
        assertTrue(((WitResult) result[0]).ok());
        assertTrue(Files.isDirectory(root.resolve("subdir")));
    }

    @Test
    public void createDirectoryAtOnExistingFails() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.createDirectory(root.resolve("subdir"));
        WitResource dir = preopen(filesystem, root, ".");

        Object[] result = filesystem.createDirectoryAt(null, new Object[] { dir, "subdir" });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("exist"), ((WitResult) result[0]).value());
    }

    @Test
    public void createDirectoryAtOnNonDirectoryBaseFails() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        WitResource dir = preopen(filesystem, root, ".");
        WitResource file = openFile(filesystem, dir, "f.txt", true, true);

        Object[] result = filesystem.createDirectoryAt(null, new Object[] { file, "subdir" });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("not-directory"), ((WitResult) result[0]).value());
    }

    @Test
    public void statReturnsDescriptorStatWithSizeAndType() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("f.txt"), "12345");
        WitResource dir = preopen(filesystem, root, ".");
        WitResource file = openFile(filesystem, dir, "f.txt", false, false);

        Object[] result = filesystem.stat(null, new Object[] { file });
        WitResult wr = (WitResult) result[0];
        assertTrue(wr.ok());
        @SuppressWarnings("unchecked")
        Map<String, Object> stat = (Map<String, Object>) wr.value();
        assertEquals(new WitEnum("regular-file"), stat.get("type"));
        assertEquals(5L, stat.get("size"));
        assertEquals(1L, stat.get("link-count"));
        assertTrue(((Optional<?>) stat.get("data-access-timestamp")).isPresent());
        assertTrue(((Optional<?>) stat.get("data-modification-timestamp")).isPresent());
        assertTrue(((Optional<?>) stat.get("status-change-timestamp")).isPresent());
    }

    @Test
    public void statAtResolvesRelativePath() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("f.txt"), "abc");
        WitResource dir = preopen(filesystem, root, ".");

        Object[] result = filesystem.statAt(null, new Object[] { dir, Set.of("symlink-follow"), "f.txt" });
        WitResult wr = (WitResult) result[0];
        assertTrue(wr.ok());
        @SuppressWarnings("unchecked")
        Map<String, Object> stat = (Map<String, Object>) wr.value();
        assertEquals(3L, stat.get("size"));
    }

    @Test
    public void statAtSandboxEscapeFails() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/sandbox");
        WitResource dir = preopen(filesystem, root, ".");

        Object[] result = filesystem.statAt(null,
                new Object[] { dir, Set.of("symlink-follow"), "../../etc/passwd" });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("access"), ((WitResult) result[0]).value());
    }

    @Test
    public void statAtOnMissingPathFails() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        WitResource dir = preopen(filesystem, fs.getPath("/"), ".");

        Object[] result = filesystem.statAt(null, new Object[] { dir, Set.of(), "missing.txt" });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("no-entry"), ((WitResult) result[0]).value());
    }

    @Test
    public void linkAtCreatesHardLink() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("orig.txt"), "content");
        WitResource dir = preopen(filesystem, root, ".");

        Object[] result = filesystem.linkAt(null,
                new Object[] { dir, Set.of(), "orig.txt", dir, "linked.txt" });
        assertTrue(((WitResult) result[0]).ok());
        assertEquals("content", Files.readString(root.resolve("linked.txt")));
    }

    @Test
    public void linkAtOnNonDirectoryDescriptorFails() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        WitResource dir = preopen(filesystem, root, ".");
        WitResource file = openFile(filesystem, dir, "f.txt", true, true);

        Object[] result = filesystem.linkAt(null, new Object[] { file, Set.of(), "a", dir, "b" });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("not-directory"), ((WitResult) result[0]).value());
    }

    @Test
    public void renameAtMovesFile() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("old.txt"), "content");
        WitResource dir = preopen(filesystem, root, ".");

        Object[] result = filesystem.renameAt(null, new Object[] { dir, "old.txt", dir, "new.txt" });
        assertTrue(((WitResult) result[0]).ok());
        assertFalse(Files.exists(root.resolve("old.txt")));
        assertEquals("content", Files.readString(root.resolve("new.txt")));
    }

    @Test
    public void renameAtOnMissingSourceFails() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        WitResource dir = preopen(filesystem, fs.getPath("/"), ".");

        Object[] result = filesystem.renameAt(null, new Object[] { dir, "missing.txt", dir, "new.txt" });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("no-entry"), ((WitResult) result[0]).value());
    }

    @Test
    public void removeDirectoryAtRemovesEmptyDirectory() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.createDirectory(root.resolve("empty"));
        WitResource dir = preopen(filesystem, root, ".");

        Object[] result = filesystem.removeDirectoryAt(null, new Object[] { dir, "empty" });
        assertTrue(((WitResult) result[0]).ok());
        assertFalse(Files.exists(root.resolve("empty")));
    }

    @Test
    public void removeDirectoryAtOnFileFails() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("f.txt"), "x");
        WitResource dir = preopen(filesystem, root, ".");

        Object[] result = filesystem.removeDirectoryAt(null, new Object[] { dir, "f.txt" });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("not-directory"), ((WitResult) result[0]).value());
    }

    @Test
    public void removeDirectoryAtOnMissingPathFails() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        WitResource dir = preopen(filesystem, fs.getPath("/"), ".");

        Object[] result = filesystem.removeDirectoryAt(null, new Object[] { dir, "missing" });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("no-entry"), ((WitResult) result[0]).value());
    }

    @Test
    public void unlinkFileAtRemovesFile() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("f.txt"), "x");
        WitResource dir = preopen(filesystem, root, ".");

        Object[] result = filesystem.unlinkFileAt(null, new Object[] { dir, "f.txt" });
        assertTrue(((WitResult) result[0]).ok());
        assertFalse(Files.exists(root.resolve("f.txt")));
    }

    @Test
    public void unlinkFileAtOnDirectoryFails() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.createDirectory(root.resolve("d"));
        WitResource dir = preopen(filesystem, root, ".");

        Object[] result = filesystem.unlinkFileAt(null, new Object[] { dir, "d" });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("is-directory"), ((WitResult) result[0]).value());
    }

    @Test
    public void metadataHashReturnsLowerUpper() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("f.txt"), "x");
        WitResource dir = preopen(filesystem, root, ".");
        WitResource file = openFile(filesystem, dir, "f.txt", false, false);

        Object[] result = filesystem.metadataHash(null, new Object[] { file });
        WitResult wr = (WitResult) result[0];
        assertTrue(wr.ok());
        @SuppressWarnings("unchecked")
        Map<String, Object> hash = (Map<String, Object>) wr.value();
        assertNotNull(hash.get("lower"));
        assertNotNull(hash.get("upper"));
        assertTrue(hash.get("lower") instanceof Long);
        assertTrue(hash.get("upper") instanceof Long);
    }

    @Test
    public void metadataHashAtResolvesRelativePath() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("f.txt"), "x");
        WitResource dir = preopen(filesystem, root, ".");

        Object[] result = filesystem.metadataHashAt(null, new Object[] { dir, Set.of(), "f.txt" });
        assertTrue(((WitResult) result[0]).ok());
    }

    @Test
    public void metadataHashAtOnMissingPathFails() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        WitResource dir = preopen(filesystem, fs.getPath("/"), ".");

        Object[] result = filesystem.metadataHashAt(null, new Object[] { dir, Set.of(), "missing.txt" });
        assertFalse(((WitResult) result[0]).ok());
        assertEquals(new WitEnum("no-entry"), ((WitResult) result[0]).value());
    }

    @Test
    public void openAtWithDirectoryFlagOnFileFails() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("f.txt"), "x");
        WitResource dir = preopen(filesystem, root, ".");

        WitResult result = openAt(filesystem, dir, "f.txt", Set.of("directory"), Set.of("read"));
        assertFalse(result.ok());
        assertEquals(new WitEnum("not-directory"), result.value());
    }

    @Test
    public void openAtOnNonDirectoryBaseFails() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        WitResource dir = preopen(filesystem, root, ".");
        WitResource file = openFile(filesystem, dir, "f.txt", true, true);

        WitResult result = openAt(filesystem, file, "anything", Set.of(), Set.of("read"));
        assertFalse(result.ok());
        assertEquals(new WitEnum("not-directory"), result.value());
    }

    @Test
    public void openAtWithoutCreateOnMissingFileFails() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        WitResource dir = preopen(filesystem, fs.getPath("/"), ".");

        WitResult result = openAt(filesystem, dir, "missing.txt", Set.of(), Set.of("read"));
        assertFalse(result.ok());
        assertEquals(new WitEnum("no-entry"), result.value());
    }

    @Test
    public void openAtSandboxEscapeFails() {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/sandbox");
        WitResource dir = preopen(filesystem, root, ".");

        WitResult result = openAt(filesystem, dir, "../../etc/passwd", Set.of(), Set.of("read"));
        assertFalse(result.ok());
        assertEquals(new WitEnum("access"), result.value());
    }

    @Test
    public void openAtExclusiveOnExistingFileFails() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.writeString(root.resolve("f.txt"), "x");
        WitResource dir = preopen(filesystem, root, ".");

        WitResult result = openAt(filesystem, dir, "f.txt", Set.of("create", "exclusive"), Set.of("read", "write"));
        assertFalse(result.ok());
        assertEquals(new WitEnum("exist"), result.value());
    }

    @Test
    public void openAtOnExistingDirectoryReturnsDirectoryDescriptorRegardlessOfDirectoryFlag() throws Exception {
        WasiFilesystemContext filesystem = newLinkedFilesystem(new WasiIoContext());
        Path root = fs.getPath("/");
        Files.createDirectory(root.resolve("d"));
        WitResource dir = preopen(filesystem, root, ".");

        WitResult result = openAt(filesystem, dir, "d", Set.of(), Set.of("read"));
        assertTrue(result.ok());
        WitResource sub = (WitResource) result.value();

        // The returned descriptor really is a directory: create-directory-at works on it.
        Object[] createResult = filesystem.createDirectoryAt(null, new Object[] { sub, "nested" });
        assertTrue(((WitResult) createResult[0]).ok());
    }

    @Test
    public void versionDefaultsToWasip2StableAndAcceptsRange() {
        WasiFilesystemContext filesystem = new WasiFilesystemContext();
        assertEquals(io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion.parse("0.2.6"),
                filesystem.getVersion());
        assertEquals(new io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion(0, 0, 1),
                filesystem.getMiniumVersion());
        assertEquals(new io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion(0, 3, 0),
                filesystem.getMaximumVersion());

        io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion newer = io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion
                .parse("0.3.0");
        assertSame(filesystem, filesystem.withVersion(newer));
        assertEquals(newer, filesystem.getVersion());

        assertThrows(IllegalArgumentException.class,
                () -> filesystem.withVersion(new io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion(9, 9, 9)));
    }
}
