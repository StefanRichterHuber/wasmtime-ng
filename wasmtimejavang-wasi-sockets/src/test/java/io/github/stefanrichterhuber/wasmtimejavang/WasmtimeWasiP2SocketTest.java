package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.WasiCliContext;

/**
 * Exercises {@code wasi:sockets} end to end: the wasm guest is the TCP
 * and UDP *client*, connecting out to plain {@code java.net} echo
 * servers this test hosts itself -- avoiding any need to coordinate an
 * ephemeral port the guest binds, since Java already knows both target
 * ports before the component ever runs (passed in as CLI arguments).
 */
public class WasmtimeWasiP2SocketTest {
    private static final String SOCKET_WASM_PATH = "target/rust-test/wasip2sockettest/wasm32-wasip2/debug/wasip2sockettest.wasm";

    private static final Logger LOGGER = LogManager.getLogger();

    @Test
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
