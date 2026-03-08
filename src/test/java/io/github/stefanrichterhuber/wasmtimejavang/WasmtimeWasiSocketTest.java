package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

public class WasmtimeWasiSocketTest {

    @Test
    public void testSocketInteractions() throws Exception {
        String wasmPath = "target/rust-test/wasip1sockettest/wasm32-wasip1/debug/wasip1sockettest.wasm";

        try (ServerSocket serverSocket = new ServerSocket(0); // Bind to any available port
                FileInputStream fis = new FileInputStream(wasmPath);
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeModule module = new WasmtimeModule(engine, fis);
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeLinker linker = new WasmtimeLinker(engine, store);) {

            int port = serverSocket.getLocalPort();

            // Prepare WASI context with the pre-opened server socket
            linker.link(new WasiPI1Context()
                    .withServerSocket(serverSocket)
                    .withStdOut(System.out)
                    .withStdErr(System.err));

            // Start a client in a separate thread to connect to the server socket
            CompletableFuture<String> clientTask = CompletableFuture.supplyAsync(() -> {
                try {
                    // Give the WASM a moment to start and call sock_accept
                    Thread.sleep(500);
                    try (Socket socket = new Socket("localhost", port);
                            BufferedWriter writer = new BufferedWriter(
                                    new OutputStreamWriter(socket.getOutputStream()));
                            BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(socket.getInputStream()))) {

                        writer.write("Hello from Java Client!");
                        writer.newLine();
                        writer.flush();

                        return reader.readLine();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            // Run the WASM module
            try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
                Object[] result = instance.invoke("_start");
                assertNotNull(result);
            }

            // Verify the client received the expected echo from WASM
            String response = clientTask.get(5, TimeUnit.SECONDS);
            assertNotNull(response);
            assertEquals("WASM echo: Hello from Java Client!", response);
        }
    }
}
