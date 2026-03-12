package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
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

            linker.link(new WasiPI1Context()
                    .withDirectory(root, ".")
                    .withStdOut(System.out)
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
        }
    }
}
