package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class WasmtimeWasiFileSystemTest {

    @Test
    public void testFileSystemInteractions() throws Exception {
        String wasmPath = "target/rust-test/wasip1filetest/wasm32-wasip1/debug/wasip1filetest.wasm";

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix().toBuilder()
                .setAttributeViews("basic", "owner", "posix", "unix").build());
                FileInputStream fis = new FileInputStream(wasmPath);
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, fis);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);) {

            Path root = fs.getPath("/");

            // 1. Prepare input.txt for the WASM
            Path inputPath = root.resolve("input.txt");
            String inputContent = "Hello from Java!";
            Files.writeString(inputPath, inputContent);

            linker.linkContext(new WasiPI1Context()
                    .withDirectory(root, ".")
                    .withStdOut(bos)
                    .withStdErr(System.err));

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                Object[] result = instance.start();
                assertNotNull(result);
            }

            // 2. Verify WASM output
            Path outputPath = root.resolve("wasm_out/output.txt");
            assertTrue(Files.exists(outputPath), "output.txt should exist");
            String outputContent = Files.readString(outputPath);
            assertEquals("WASM received: " + inputContent, outputContent);

            // 3. Verify directory creation
            Path wasmOutDir = root.resolve("wasm_out");
            assertTrue(Files.isDirectory(wasmOutDir), "wasm_out should be a directory");

            Path renameDir = root.resolve("test_rename_dir");
            assertTrue(Files.isDirectory(renameDir), "test_rename_dir should be a directory");

            Path movedFile = renameDir.resolve("moved.txt");
            assertTrue(Files.exists(movedFile), "moved.txt should exist in renameDir");
            assertEquals("rename test", Files.readString(movedFile));

            /*
             * Reading 'input.txt'...
             * Content of 'input.txt': Hello from Java!
             * Creating directory 'wasm_out'...
             * Writing to 'wasm_out/output.txt'...
             * Testing rename...
             * Testing FD operations...
             * fd_allocate...
             * fd_filestat_set_size...
             * fd_seek...
             * fd_sync / fd_datasync...
             * fd_fdstat_get...
             * FD flags: 0
             * fd_fdstat_set_flags...
             * fd_pread...
             * fd_readdir...
             * Found entry: "fd_test.txt"
             * Found entry: "input.txt"
             * Found entry: "test_rename_dir"
             * Found entry: "wasm_out"
             * Found entry: "work"
             * fd_renumber...
             * Testing Path operations...
             * path_link...
             * path_filestat_get...
             * File size: 16
             * path_filestat_set_times...
             * path_unlink_file...
             * path_remove_directory...
             * Done!
             * 
             * 
             */

            String content = bos.toString(StandardCharsets.UTF_8);
            assertTrue(content.contains("Reading 'input.txt'...\n"));
            assertTrue(content.contains("Content of 'input.txt': Hello from Java!\n"));
            assertTrue(content.contains("Creating directory 'wasm_out'...\n"));
            assertTrue(content.contains("Writing to 'wasm_out/output.txt'...\n"));
            assertTrue(content.contains("Testing rename...\n"));
            assertTrue(content.contains("Testing FD operations...\n"));
            assertTrue(content.contains("fd_allocate...\n"));
            assertTrue(content.contains("fd_filestat_set_size...\n"));
            assertTrue(content.contains("fd_seek...\n"));
            assertTrue(content.contains("fd_sync / fd_datasync...\n"));
            assertTrue(content.contains("fd_fdstat_get...\n"));
            assertTrue(content.contains("FD flags: 0\n"));
            assertTrue(content.contains("fd_fdstat_set_flags...\n"));
            assertTrue(content.contains("fd_pread...\n"));
            assertTrue(content.contains("fd_readdir...\n"));
            assertTrue(content.contains("Found entry: \"fd_test.txt\"\n"));
            assertTrue(content.contains("Found entry: \"input.txt\"\n"));
            assertTrue(content.contains("Found entry: \"test_rename_dir\"\n"));
            assertTrue(content.contains("Found entry: \"wasm_out\"\n"));
            assertTrue(content.contains("Found entry: \"work\"\n"));
            assertTrue(content.contains("fd_renumber...\n"));
            assertTrue(content.contains("Testing Path operations...\n"));
            assertTrue(content.contains("path_link...\n"));
            assertTrue(content.contains("path_filestat_get...\n"));
            assertTrue(content.contains("File size: 16\n"));
            assertTrue(content.contains("path_filestat_set_times...\n"));
            assertTrue(content.contains("path_unlink_file...\n"));
            assertTrue(content.contains("path_remove_directory...\n"));
            assertTrue(content.contains("Done!\n"));
        }
    }
}
