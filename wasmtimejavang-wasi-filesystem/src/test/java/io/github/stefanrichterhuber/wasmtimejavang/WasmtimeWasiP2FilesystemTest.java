package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.WasiCliContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.WasiFilesystemContext;

/**
 * Exercises {@code wasi:filesystem/types} and {@code wasi:filesystem/preopens}
 * end to end against a sandboxed {@link Jimfs} filesystem (rather than the
 * host filesystem), covering the same operations {@code
 * WasmtimeWasiFileSystemTest} (WASI Preview 1, in the core module) exercises:
 * read, create/write, rename, seek/truncate, sync, set times, readdir,
 * metadata, hard link, remove, and sandbox-escape prevention.
 */
public class WasmtimeWasiP2FilesystemTest {
    private static final String FILE_WASM_PATH = "target/rust-test/wasip2filetest/wasm32-wasip2/debug/wasip2filetest.wasm";

    private static final Logger LOGGER = LogManager.getLogger();

    @Test
    @Disabled("Fails for github pipeline")
    public void wasip2filetest() throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        LOGGER.info("Started wasip2filetest");

        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix().toBuilder()
                .setAttributeViews("basic", "owner", "posix", "unix").build());
                FileInputStream fis = new FileInputStream(FILE_WASM_PATH);
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeComponent component = new WasmtimeComponent(engine, fis);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeComponentLinker linker = new WasmtimeComponentLinker(engine, store)) {

            Path root = fs.getPath("/");
            Path inputPath = root.resolve("input.txt");
            String inputContent = "Hello from Java!";
            Files.writeString(inputPath, inputContent);

            assertTrue(component.isCommand(), "component does not export wasi:cli/run");
            assertTrue(component.getImportInterfaces().stream()
                    .anyMatch(n -> n.startsWith("wasi:filesystem/types@")));

            linker.linkContext(new WasiCliContext().withStdOut(stdout));
            linker.linkContext(new WasiFilesystemContext().withDirectory(root, "."));
            linker.linkRequired(component);

            try (WasmtimeComponentInstance instance = new WasmtimeComponentInstance(store, component, linker)) {
                WitResult status = instance.asCliRunnable().call();
                assertTrue(status.ok(), "wasi:cli/run#run did not return Ok");
            }

            Path outputPath = root.resolve("wasm_out/output.txt");
            assertTrue(Files.exists(outputPath), "output.txt should exist");
            assertEquals("WASM received: " + inputContent, Files.readString(outputPath));

            Path movedFile = root.resolve("test_rename_dir/moved.txt");
            assertTrue(Files.exists(movedFile), "moved.txt should exist after rename");
            assertEquals("rename test", Files.readString(movedFile));

            Path fdTestFile = root.resolve("fd_test.txt");
            assertTrue(Files.exists(fdTestFile), "fd_test.txt should exist");
            assertEquals("01234567XYZ", Files.readString(fdTestFile));

            assertFalse(Files.exists(root.resolve("hardlink_to_input.txt")), "hard link should have been removed");
            assertFalse(Files.exists(root.resolve("dir_to_remove")), "dir_to_remove should have been removed");
        }

        String output = stdout.toString("UTF-8");
        assertTrue(output.contains("Content of 'input.txt': Hello from Java!"), "stdout was: " + output);
        assertTrue(output.contains("File size: 16"), "stdout was: " + output);
        assertTrue(output.contains("Is file: true"), "stdout was: " + output);
        assertTrue(output.contains("Is dir: true"), "stdout was: " + output);
        assertTrue(output.contains("Correctly failed to escape sandbox"), "stdout was: " + output);
        assertTrue(output.contains("Done!"), "stdout was: " + output);

        LOGGER.info("Stopped wasip2filetest");
    }
}
