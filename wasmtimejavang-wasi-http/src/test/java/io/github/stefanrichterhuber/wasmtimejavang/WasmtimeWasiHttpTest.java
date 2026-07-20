package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2http.WasiHttpContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasicli.WasiCliContext;

/**
 * End-to-end test of {@link WasiHttpContext}'s
 * {@code wasi:http/outgoing-handler}
 * support: a compiled component ({@code wasip2httptest}, the only fixture
 * besides {@code wasip2customtest} using {@code wit_bindgen::generate!},
 * since {@code wasi:http} isn't part of {@code wasm32-wasip2}'s built-in WASI
 * componentization) makes a real outgoing HTTP GET against a local
 * {@link HttpServer} and prints the response to stdout.
 * <br>
 * The target port is passed to the guest via {@code wasi:cli/environment}
 * (already supported by {@link WasiCliContext}) rather than a fixed port,
 * since the local server binds an ephemeral one.
 */
public class WasmtimeWasiHttpTest {
    private static final String WASM_PATH = "target/rust-test/wasip2httptest/wasm32-wasip2/debug/wasip2httptest.wasm";

    @Test
    public void wasip2httptest() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hello", exchange -> {
            byte[] body = "hi there".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        try {
            try (
                    FileInputStream fis = new FileInputStream(WASM_PATH);
                    WasmtimeEngine engine = new WasmtimeEngine();
                    WasmtimeComponent component = new WasmtimeComponent(engine, fis);
                    WasmtimeStore store = new WasmtimeStore(engine);
                    WasmtimeComponentLinker linker = new WasmtimeComponentLinker(engine, store)) {

                assertTrue(
                        component.getImportInterfaces().stream()
                                .anyMatch(n -> n.startsWith("wasi:http/outgoing-handler")),
                        "component does not import wasi:http/outgoing-handler");

                linker.linkContext(new WasiCliContext().withStdOut(stdout)
                        .withEnvs(Map.of("WASIP2_HTTP_PORT", String.valueOf(server.getAddress().getPort()))));
                linker.linkContext(new WasiHttpContext());
                linker.linkRequired(component); // pulls in wasi-io, wasi-clocks, wasi-random

                try (WasmtimeComponentInstance instance = new WasmtimeComponentInstance(store, component, linker)) {
                    WitResult status = instance.asCliRunnable().call();
                    assertTrue(status.ok(), "wasi:cli/run#run did not return Ok");
                }
            }
        } finally {
            server.stop(0);
        }

        String output = stdout.toString("UTF-8");
        assertTrue(output.contains("STATUS=200"), "stdout was: " + output);
        assertTrue(output.contains("BODY=hi there"), "stdout was: " + output);
        assertTrue(output.contains("Done!"), "stdout was: " + output);
    }
}
