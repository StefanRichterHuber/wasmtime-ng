package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.ProcExitException;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.WasiCliContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.WasiFilesystemContext;

public class WasmtimeWasiP2Test {
    private static final String WASM_PATH = "target/rust-test/wasip2test/wasm32-wasip2/debug/wasip2test.wasm";
    private static final String CLI_WASM_PATH = "target/rust-test/wasip2clitest/wasm32-wasip2/debug/wasip2clitest.wasm";
    private static final String RANDOM_WASM_PATH = "target/rust-test/wasip2randomtest/wasm32-wasip2/debug/wasip2randomtest.wasm";
    private static final String SOCKET_WASM_PATH = "target/rust-test/wasip2sockettest/wasm32-wasip2/debug/wasip2sockettest.wasm";
    private static final String FILE_WASM_PATH = "target/rust-test/wasip2filetest/wasm32-wasip2/debug/wasip2filetest.wasm";

    private static final Logger LOGGER = LogManager.getLogger();

    @Test
    public void wasip2test() throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        LOGGER.info("Started wasip2test");
        try (
                FileInputStream fis = new FileInputStream(WASM_PATH);
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeComponent component = new WasmtimeComponent(engine, fis);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeComponentLinker linker = new WasmtimeComponentLinker(engine, store)) {

            // Interfaces are read straight off the compiled component via wasmtime,
            // no instantiation or linking required.
            assertTrue(component.isCommand(), "component does not export wasi:cli/run");
            assertTrue(component.getExportInterfaces().stream().anyMatch(n -> n.startsWith("wasi:cli/run@")));
            assertTrue(component.getImportInterfaces().stream().anyMatch(n -> n.startsWith("wasi:io/poll@")));
            assertTrue(component.getImportInterfaces().stream()
                    .anyMatch(n -> n.startsWith("wasi:clocks/monotonic-clock@")));
            assertTrue(component.getImportInterfaces().stream().anyMatch(n -> n.startsWith("wasi:cli/stdout@")));

            // Link a configured "wasi-cli" explicitly to capture stdout; everything
            // else this specific component actually needs ("wasi-io", "wasi-clocks")
            // is discovered from its declared imports and auto-linked via the
            // linker's default ServiceLoaderComponentContextLookup (backed by
            // META-INF/services/...WasmComponentContext) -- nothing this component
            // doesn't use (e.g. wasi:filesystem, wasi:sockets) gets linked.
            linker.linkContext(new WasiCliContext().withStdOut(stdout));
            linker.linkRequired(component);

            try (WasmtimeComponentInstance instance = new WasmtimeComponentInstance(store, component, linker)) {
                WitResult status = instance.asCliRunnable().call();
                assertTrue(status.ok(), "wasi:cli/run#run did not return Ok");
            }
        }

        String output = stdout.toString("UTF-8");
        assertTrue(output.contains("Hello, world!"), "stdout was: " + output);
        assertTrue(output.contains("Napped for"), "stdout was: " + output);
        LOGGER.info("Stopped wasip2test");
    }

    /**
     * Covers the wasi:cli/wasip2 core surface {@link #wasip2test()} doesn't
     * touch: program arguments, environment variables, stderr, reading from a
     * prepared stdin, terminal detection (always "not a tty"), the wall
     * clock, and a non-zero {@code wasi:cli/exit} exit code.
     */
    @Test
    @Disabled("Hangs in github pipeline")
    public void wasip2clitest() throws Exception {
        LOGGER.info("Started wasip2clitest");
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ByteArrayInputStream stdin = new ByteArrayInputStream("hello from a prepared stdin".getBytes("UTF-8"));

        try (
                FileInputStream fis = new FileInputStream(CLI_WASM_PATH);
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeComponent component = new WasmtimeComponent(engine, fis);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeComponentLinker linker = new WasmtimeComponentLinker(engine, store)) {

            linker.linkContext(new WasiCliContext()
                    .withStdOut(stdout)
                    .withStdErr(stderr)
                    .withStdIn(stdin)
                    .withEnvs(Map.of("FOO", "BAR"))
                    .withArguments(List.of("wasip2clitest", "arg1", "arg2")));
            linker.linkRequired(component);

            try (WasmtimeComponentInstance instance = new WasmtimeComponentInstance(store, component, linker)) {
                // WASI Preview 2's exit(status: result<_, _>) can only convey
                // success/failure, unlike WASI Preview 1's proc_exit(rval: u32) --
                // so a non-zero process::exit(1) in the wasm program surfaces
                // here as exit code 1, not whatever specific code was passed.
                ProcExitException exit = assertThrows(ProcExitException.class,
                        () -> instance.asCliRunnable().call());
                assertEquals(1, exit.getCode());
            }
        }

        String output = stdout.toString("UTF-8");
        assertTrue(output.contains("Hello from wasip2clitest!"), "stdout was: " + output);
        assertTrue(output.contains("ARGS [\"wasip2clitest\", \"arg1\", \"arg2\"]"), "stdout was: " + output);
        assertTrue(output.contains("ENV FOO=BAR"), "stdout was: " + output);
        assertTrue(output.contains("STDIN=hello from a prepared stdin"), "stdout was: " + output);
        assertTrue(output.contains("STDIN_TERMINAL=false"), "stdout was: " + output);
        assertTrue(output.contains("STDOUT_TERMINAL=false"), "stdout was: " + output);
        assertTrue(output.contains("STDERR_TERMINAL=false"), "stdout was: " + output);
        assertTrue(output.contains("WALL_CLOCK_SECONDS="), "stdout was: " + output);

        String errOutput = stderr.toString("UTF-8");
        assertTrue(errOutput.contains("stderr line from wasip2clitest"), "stderr was: " + errOutput);
        LOGGER.info("Stopped wasip2clitest");
    }

    @Test
    public void wasip2randomtest() throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        LOGGER.info("Started wasip2randomtest");
        try (
                FileInputStream fis = new FileInputStream(RANDOM_WASM_PATH);
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeComponent component = new WasmtimeComponent(engine, fis);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeComponentLinker linker = new WasmtimeComponentLinker(engine, store)) {

            assertTrue(component.isCommand(), "component does not export wasi:cli/run");
            // Link WasiCliContext so we can capture stdout of the println calls in our rust
            // code
            linker.linkContext(new WasiCliContext().withStdOut(stdout));

            // WasiRandomContext will be auto-linked because of the ServiceLoader mechanism
            linker.linkRequired(component);

            try (WasmtimeComponentInstance instance = new WasmtimeComponentInstance(store, component, linker)) {
                WitResult status = instance.asCliRunnable().call();
                assertTrue(status.ok(), "wasi:cli/run#run did not return Ok");
            }
        }

        String output = stdout.toString("UTF-8");
        assertTrue(output.contains("RANDOM BYTES="), "stdout was: " + output);
        assertTrue(output.contains("HASH MAP SIZE=2"), "stdout was: " + output);
        LOGGER.info("Stopped wasip2randomtest");
    }

    /**
     * Exercises {@code wasi:filesystem/types} and {@code
     * wasi:filesystem/preopens} end to end against a sandboxed
     * {@link Jimfs} filesystem (rather than the host filesystem), covering
     * the same operations {@code WasmtimeWasiFileSystemTest} exercises for
     * WASI Preview 1: read, create/write, rename, seek/truncate, sync, set
     * times, readdir, metadata, hard link, remove, and sandbox-escape
     * prevention.
     */
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

    /**
     * Exercises {@code wasi:sockets} end to end: the wasm guest is the TCP
     * and UDP *client*, connecting out to plain {@code java.net} echo
     * servers this test hosts itself -- avoiding any need to coordinate an
     * ephemeral port the guest binds, since Java already knows both target
     * ports before the component ever runs (passed in as CLI arguments).
     */
    @Test
    // @Disabled("No sockets for automated builds")
    public void wasip2sockettest() throws Exception {
        LOGGER.info("Started wasip2sockettest");
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        try (ServerSocket tcpServer = new ServerSocket(0);
                DatagramSocket udpServer = new DatagramSocket(0);
                FileInputStream fis = new FileInputStream(SOCKET_WASM_PATH);
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeComponent component = new WasmtimeComponent(engine, fis);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeComponentLinker linker = new WasmtimeComponentLinker(engine, store)) {

            int tcpPort = tcpServer.getLocalPort();
            int udpPort = udpServer.getLocalPort();

            assertTrue(component.getImportInterfaces().stream().anyMatch(n -> n.startsWith("wasi:sockets/tcp@")));

            CompletableFuture<String> tcpTask = CompletableFuture.supplyAsync(() -> {
                try (Socket client = tcpServer.accept();
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(client.getInputStream(), "UTF-8"));
                        OutputStream out = client.getOutputStream()) {
                    String line = reader.readLine();
                    out.write((line + "\n").getBytes("UTF-8"));
                    out.flush();
                    return line;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            CompletableFuture<String> udpTask = CompletableFuture.supplyAsync(() -> {
                try {
                    byte[] buf = new byte[128];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    udpServer.receive(packet);
                    String received = new String(packet.getData(), packet.getOffset(), packet.getLength(), "UTF-8");
                    udpServer.send(new DatagramPacket(packet.getData(), packet.getOffset(), packet.getLength(),
                            packet.getAddress(), packet.getPort()));
                    return received;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            linker.linkContext(new WasiCliContext().withStdOut(stdout)
                    .withArguments(List.of("wasip2sockettest", String.valueOf(tcpPort), String.valueOf(udpPort))));
            linker.linkRequired(component);

            try (WasmtimeComponentInstance instance = new WasmtimeComponentInstance(store, component, linker)) {
                WitResult status = instance.asCliRunnable().call();
                assertTrue(status.ok(), "wasi:cli/run#run did not return Ok");
            }

            assertEquals("Hello TCP from wasm", tcpTask.get(10, TimeUnit.SECONDS));
            assertEquals("Hello UDP from wasm", udpTask.get(10, TimeUnit.SECONDS));
        }

        String output = stdout.toString("UTF-8");
        assertTrue(output.contains("TCP received: Hello TCP from wasm"), "stdout was: " + output);
        assertTrue(output.contains("UDP received: Hello UDP from wasm"), "stdout was: " + output);
        assertTrue(output.contains("Done!"), "stdout was: " + output);
        LOGGER.info("Stppped wasip2sockettest");
    }
}
