package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class WasmtimeWasiVFSTest {

    @Test
    public void testVFSIsolation() throws Exception {
        String wasmPath = "target/rust-test/wasip1vfstest/wasm32-wasip1/debug/wasip1vfstest.wasm";

        // Create a file on the host file system
        Path hostFile = Files.createTempFile("wasm-host-test", ".txt");
        Files.writeString(hostFile, "Host file content");

        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix().toBuilder()
                .setAttributeViews("basic", "owner", "posix", "unix").build());
                FileInputStream fis = new FileInputStream(wasmPath);
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, fis);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Path root = fs.getPath("/");
            Files.writeString(root.resolve("input.txt"), "Jimfs file content");

            linker.linkContext(new WasiPI1Context()
                    .withDirectory(root, "/tmp")
                    .withArguments(List.of("wasip1vfstest", hostFile.toAbsolutePath().toString()))
                    .withStdOut(baos)
                    .withStdErr(baos));

            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                instance.invoke("_start");
            }

            String output = baos.toString();
            System.out.println(output);

            // Verify the output contains messages about failing to open host files
            assertTrue(output.contains("Correctly failed to open '/etc/passwd'"), "Should fail to open /etc/passwd");
            assertTrue(output.contains("Correctly failed to open '../etc/passwd'"), "Should fail to escape with ..");
            assertTrue(output.contains("Correctly failed to open host-specific file"),
                    "Should fail to open host-specific file");

            // Verify it could open the Jimfs file
            assertTrue(output.contains("Successfully opened 'input.txt'"),
                    "Should succeed to open input.txt in Jimfs");

            // Double check that it didn't mistakenly report success
            assertFalse(output.contains("Error: Successfully opened"),
                    "Should not have successfully opened any host files");

        } finally {
            Files.deleteIfExists(hostFile);
        }
    }
}
