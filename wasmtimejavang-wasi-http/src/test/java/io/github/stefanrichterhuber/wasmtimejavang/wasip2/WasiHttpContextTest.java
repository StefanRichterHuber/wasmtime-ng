package io.github.stefanrichterhuber.wasmtimejavang.wasip2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2http.WasiHttpContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiio.WasiIoContext;

/**
 * Direct unit tests for {@link WasiHttpContext}, wiring its {@code "wasi-io"}
 * dependency by hand (a real {@link WasiIoContext}) and driving the
 * {@code fields}/{@code outgoing-request}/{@code outgoing-handler} methods
 * directly against a real local {@link HttpServer} -- there's no compiled
 * {@code .wasm} fixture exercising this path yet (see
 * {@code WasmtimeWasiHttpTest} for that), so this is the only coverage of the
 * actual outgoing HTTP call today.
 */
public class WasiHttpContextTest {

    private HttpServer server;

    @AfterEach
    public void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private static WasiHttpContext newLinkedHttp(WasiIoContext io) {
        WasiHttpContext http = new WasiHttpContext();
        http.onDependenciesResolved(
                (name, version) -> WasiIoContext.NAME.equals(name) ? Optional.of(io) : Optional.empty());
        return http;
    }

    private int startServer(String path, int status, String responseBody, String echoHeaderName)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> handle(exchange, status, responseBody, echoHeaderName));
        server.start();
        return server.getAddress().getPort();
    }

    private static void handle(HttpExchange exchange, int status, String responseBody, String echoHeaderName)
            throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        byte[] responseBytes = "HEAD".equals(exchange.getRequestMethod()) ? new byte[0]
                : (responseBody + " method=" + exchange.getRequestMethod() + " body=" + requestBody.length)
                        .getBytes(StandardCharsets.UTF_8);
        if (echoHeaderName != null) {
            String value = exchange.getRequestHeaders().getFirst(echoHeaderName);
            if (value != null) {
                exchange.getResponseHeaders().add("x-echo", value);
            }
        }
        exchange.sendResponseHeaders(status, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    // ---- identity / dependency wiring ----------------------------------------

    @Test
    public void nameProvidedInterfacesAndDependencies() {
        WasiHttpContext http = new WasiHttpContext();
        assertEquals(WasiHttpContext.NAME, http.name());
        assertEquals(Set.of("wasi:http/types", "wasi:http/outgoing-handler"), http.getProvidedInterfaces());
        assertEquals(List.of(WasiIoContext.NAME), http.getDependencies());
    }

    @Test
    public void implementsWasmComponentContext() {
        assertTrue(WasmComponentContext.class.isAssignableFrom(WasiHttpContext.class));
    }

    @Test
    public void onDependenciesResolvedThrowsWhenWasiIoIsMissing() {
        WasiHttpContext http = new WasiHttpContext();
        ComponentContextLookup emptyLookup = (name, version) -> Optional.empty();
        assertThrows(IllegalStateException.class, () -> http.onDependenciesResolved(emptyLookup));
    }

    // ---- fields ---------------------------------------------------------------

    @Test
    public void fieldsSetGetHasDeleteAppendAreCaseInsensitive() {
        WasiHttpContext http = newLinkedHttp(new WasiIoContext());
        WitResource headers = http.fields(null);

        WitResult setResult = http.fieldsSet(null, headers, "Content-Type",
                List.of("text/plain".getBytes(StandardCharsets.UTF_8)));
        assertTrue(setResult.ok());
        assertTrue(http.fieldsHas(null, headers, "content-type"));
        assertEquals(1, http.fieldsGet(null, headers, "CONTENT-TYPE").size());

        http.fieldsAppend(null, headers, "X-Multi", "a".getBytes(StandardCharsets.UTF_8));
        http.fieldsAppend(null, headers, "x-multi", "b".getBytes(StandardCharsets.UTF_8));
        assertEquals(2, http.fieldsGet(null, headers, "X-MULTI").size());

        WitResult deleteResult = http.fieldsDelete(null, headers, "content-type");
        assertTrue(deleteResult.ok());
        assertFalse(http.fieldsHas(null, headers, "Content-Type"));

        List<Object> entries = http.fieldsEntries(null, headers);
        assertEquals(2, entries.size());
    }

    @Test
    public void fieldsCloneIsIndependentCopy() {
        WasiHttpContext http = newLinkedHttp(new WasiIoContext());
        WitResource original = http.fields(null);
        http.fieldsAppend(null, original, "a", "1".getBytes(StandardCharsets.UTF_8));

        WitResource clone = http.fieldsClone(null, original);
        http.fieldsAppend(null, clone, "b", "2".getBytes(StandardCharsets.UTF_8));

        assertEquals(1, http.fieldsEntries(null, original).size());
        assertEquals(2, http.fieldsEntries(null, clone).size());
    }

    @Test
    public void fieldsFromListBuildsAMutableFields() {
        WasiHttpContext http = newLinkedHttp(new WasiIoContext());
        List<Object> entries = new java.util.ArrayList<>();
        entries.add(new Object[] { "a", "1".getBytes(StandardCharsets.UTF_8) });
        WitResult result = http.fieldsFromList(null, entries);
        assertTrue(result.ok());
        WitResource headers = (WitResource) result.value();
        assertTrue(http.fieldsHas(null, headers, "a"));
        assertTrue(http.fieldsSet(null, headers, "b", List.of("2".getBytes(StandardCharsets.UTF_8))).ok());
    }

    @Test
    public void immutableFieldsRejectMutation() throws IOException {
        WasiIoContext io = new WasiIoContext();
        WasiHttpContext http = newLinkedHttp(io);
        int port = startServer("/immutable", 200, "ok", null);
        // incoming-response headers are registered internally as immutable;
        // simulate the same shape via a round trip through the real server.
        WitResource request = buildRequest(http, io, "GET", "/immutable", null, null);
        setAuthority(http, request, port);
        WitResult handled = handleAndAwait(http, request);
        assertTrue(handled.ok());
        WitResource response = (WitResource) handled.value();
        WitResource responseHeaders = http.incomingResponseHeaders(null, response);
        WitResult setResult = http.fieldsSet(null, responseHeaders, "x", List.of());
        assertFalse(setResult.ok());
        assertEquals("immutable", ((WitVariant) setResult.value()).caseName());
    }

    // ---- outgoing-request / outgoing-handler ----------------------------------

    private WitResource buildRequest(WasiHttpContext http, WasiIoContext io, String method, String path,
            String bodyText, String headerName) {
        WitResource headers = http.fields(null);
        if (headerName != null) {
            http.fieldsAppend(null, headers, headerName, "hello".getBytes(StandardCharsets.UTF_8));
        }
        WitResource request = http.outgoingRequest(null, headers);
        http.outgoingRequestSetMethod(null, request, new WitVariant(method.toLowerCase(), null));
        http.outgoingRequestSetPathWithQuery(null, request, Optional.of(path));
        http.outgoingRequestSetScheme(null, request, Optional.of(new WitVariant("HTTP", null)));

        WitResult bodyResult = http.outgoingRequestBody(null, request);
        assertTrue(bodyResult.ok());
        WitResource body = (WitResource) bodyResult.value();
        if (bodyText != null) {
            WitResult writeStreamResult = http.outgoingBodyWrite(null, body);
            assertTrue(writeStreamResult.ok());
            WitResource stream = (WitResource) writeStreamResult.value();
            OutputStream out = io.getOutputStream(stream.rep());
            try {
                out.write(bodyText.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        WitResult finishResult = http.outgoingBodyFinish(null, body, Optional.empty());
        assertTrue(finishResult.ok());
        return request;
    }

    /**
     * Drives {@code handle} + {@code future-incoming-response.get}, returning the
     * inner (network-level) result.
     */
    private WitResult handleAndAwait(WasiHttpContext http, WitResource request) {
        WitResult handleResult = http.outgoingHandlerHandle(null, request, Optional.empty());
        assertTrue(handleResult.ok(), "handle failed: " + handleResult.value());
        WitResource future = (WitResource) handleResult.value();
        Optional<Object> got = http.futureIncomingResponseGet(null, future);
        assertTrue(got.isPresent());
        WitResult outer = (WitResult) got.get();
        assertTrue(outer.ok(), "future-incoming-response.get() outer result should be Ok on first call");
        return (WitResult) outer.value();
    }

    private void setAuthority(WasiHttpContext http, WitResource request, int port) {
        http.outgoingRequestSetAuthority(null, request, Optional.of("127.0.0.1:" + port));
    }

    @Test
    public void roundTripsAGetRequestThroughARealServer() throws IOException {
        WasiIoContext io = new WasiIoContext();
        WasiHttpContext http = newLinkedHttp(io);
        int port = startServer("/hello", 200, "hi", "x-request-id");

        WitResource request = buildRequest(http, io, "GET", "/hello", null, "x-request-id");
        setAuthority(http, request, port);

        WitResult outer = handleAndAwait(http, request);
        assertTrue(outer.ok(), "future resolved to error: " + outer.value());
        WitResource response = (WitResource) outer.value();

        assertEquals(200, http.incomingResponseStatus(null, response));
        WitResource responseHeaders = http.incomingResponseHeaders(null, response);
        assertTrue(http.fieldsHas(null, responseHeaders, "x-echo"));

        WitResult consumeResult = http.incomingResponseConsume(null, response);
        assertTrue(consumeResult.ok());
        WitResource incomingBody = (WitResource) consumeResult.value();
        WitResult streamResult = http.incomingBodyStream(null, incomingBody);
        assertTrue(streamResult.ok());
        WitResource stream = (WitResource) streamResult.value();
        InputStream in = io.getInputStream(stream.rep());
        byte[] body = in.readAllBytes();
        assertEquals("hi method=GET body=0", new String(body, StandardCharsets.UTF_8));

        WitResource trailers = http.incomingBodyFinish(null, incomingBody);
        Optional<Object> trailersResult = http.futureTrailersGet(null, trailers);
        assertTrue(trailersResult.isPresent());
        WitResult trailersOuter = (WitResult) trailersResult.get();
        assertTrue(trailersOuter.ok());
        WitResult trailersInner = (WitResult) trailersOuter.value();
        assertTrue(trailersInner.ok());
        assertEquals(Optional.empty(), trailersInner.value());
    }

    @Test
    public void roundTripsAPostRequestWithABody() throws IOException {
        WasiIoContext io = new WasiIoContext();
        WasiHttpContext http = newLinkedHttp(io);
        int port = startServer("/echo", 201, "posted", null);

        WitResource request = buildRequest(http, io, "POST", "/echo", "payload", null);
        setAuthority(http, request, port);

        WitResult outer = handleAndAwait(http, request);
        assertTrue(outer.ok());
        WitResource response = (WitResource) outer.value();
        assertEquals(201, http.incomingResponseStatus(null, response));

        WitResource incomingBody = (WitResource) http.incomingResponseConsume(null, response).value();
        WitResource stream = (WitResource) http.incomingBodyStream(null, incomingBody).value();
        byte[] body = io.getInputStream(stream.rep()).readAllBytes();
        assertEquals("posted method=POST body=7", new String(body, StandardCharsets.UTF_8));
    }

    @Test
    public void futureIncomingResponseGetIsConsumedAtMostOnce() throws IOException {
        WasiIoContext io = new WasiIoContext();
        WasiHttpContext http = newLinkedHttp(io);
        int port = startServer("/once", 200, "ok", null);

        WitResource request = buildRequest(http, io, "GET", "/once", null, null);
        setAuthority(http, request, port);
        WitResult handleResult = http.outgoingHandlerHandle(null, request, Optional.empty());
        WitResource future = (WitResource) handleResult.value();

        Optional<Object> first = http.futureIncomingResponseGet(null, future);
        assertTrue(first.isPresent());
        assertTrue(((WitResult) first.get()).ok());

        Optional<Object> second = http.futureIncomingResponseGet(null, future);
        assertTrue(second.isPresent());
        assertFalse(((WitResult) second.get()).ok());
    }

    @Test
    public void handleFailsWithUriInvalidWhenAuthorityIsMissing() {
        WasiIoContext io = new WasiIoContext();
        WasiHttpContext http = newLinkedHttp(io);
        WitResource request = buildRequest(http, io, "GET", "/no-authority", null, null);
        WitResult result = http.outgoingHandlerHandle(null, request, Optional.empty());
        assertFalse(result.ok());
        assertEquals("HTTP-request-URI-invalid", ((WitVariant) result.value()).caseName());
    }

    @Test
    public void futureResolvesToConnectionRefusedForAClosedPort() throws IOException {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        }

        WasiIoContext io = new WasiIoContext();
        WasiHttpContext http = newLinkedHttp(io);
        WitResource request = buildRequest(http, io, "GET", "/", null, null);
        setAuthority(http, request, closedPort);

        WitResult handleResult = http.outgoingHandlerHandle(null, request, Optional.empty());
        assertTrue(handleResult.ok(), "handle itself should still succeed: " + handleResult.value());
        WitResource future = (WitResource) handleResult.value();

        Optional<Object> got = http.futureIncomingResponseGet(null, future);
        assertTrue(got.isPresent());
        WitResult outer = (WitResult) got.get();
        assertTrue(outer.ok(), "future-incoming-response.get() outer result should be ok on first call");
        WitResult inner = (WitResult) outer.value();
        assertFalse(inner.ok());
        assertEquals("connection-refused", ((WitVariant) inner.value()).caseName());
    }

    @Test
    public void requestOptionsStoreAndReportTimeouts() {
        WasiHttpContext http = newLinkedHttp(new WasiIoContext());
        WitResource options = http.requestOptions(null);
        assertEquals(Optional.empty(), http.requestOptionsConnectTimeout(null, options));

        http.requestOptionsSetConnectTimeout(null, options, Optional.of(1_000_000_000L));
        assertEquals(Optional.of(1_000_000_000L), http.requestOptionsConnectTimeout(null, options));

        http.requestOptionsSetFirstByteTimeout(null, options, Optional.of(2_000_000_000L));
        assertEquals(Optional.of(2_000_000_000L), http.requestOptionsFirstByteTimeout(null, options));

        http.requestOptionsSetBetweenBytesTimeout(null, options, Optional.of(3_000_000_000L));
        assertEquals(Optional.of(3_000_000_000L), http.requestOptionsBetweenBytesTimeout(null, options));
    }

    @Test
    public void httpErrorCodeAlwaysReportsEmpty() {
        WasiHttpContext http = newLinkedHttp(new WasiIoContext());
        assertEquals(Optional.empty(), http.typesHttpErrorCode(null, WitResource.borrow("io-error", 1)));
    }
}
