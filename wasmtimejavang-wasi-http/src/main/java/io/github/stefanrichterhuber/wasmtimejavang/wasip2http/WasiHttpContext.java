package io.github.stefanrichterhuber.wasmtimejavang.wasip2http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup;
import io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasihttp.OutgoingHandlerContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasihttp.TypesContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiio.WasiIoContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiio.WasiIoResources;

/**
 * Implementation of {@code wasi:http/types} and
 * {@code wasi:http/outgoing-handler}
 * (WASI Preview 2, 0.2.8) -- the {@code "wasi-http"} component context.
 * <br>
 * Only the outgoing-request direction (a guest making outbound HTTP calls,
 * e.g. {@code fetch()}) is actually exercised: {@link #outgoingHandlerHandle}
 * performs a real, blocking call via {@link HttpClient}. {@code types} also
 * declares the {@code incoming-request}/{@code outgoing-response}/
 * {@code response-outparam} resources belonging to the (not yet implemented)
 * {@code wasi:http/incoming-handler} direction -- Java requires implementing
 * every abstract method of an implemented interface, so those get real,
 * table-backed bodies too even though no code path constructs one yet;
 * they're reserved for a future {@code incoming-handler} context to build on.
 * <br>
 * Every host call in this bridge is already synchronous from the guest's
 * perspective (same rationale {@code WasiSocketsContext} documents for its
 * own two-phase operations), so {@code outgoing-handler.handle} performs the
 * real HTTP call immediately and returns a {@code future-incoming-response}
 * that's already resolved, and {@code incoming-body.finish} returns a
 * {@code future-trailers} that always resolves to "no trailers" -- no actual
 * completion-driven pollable is needed, only the existing {@code ALWAYS_READY}
 * sentinel {@link WasiIoResources} already provides. Per the WIT contract,
 * network-level failures (refused connection, timeout, unresolvable host) are
 * reported through {@code future-incoming-response.get()}, not
 * {@code outgoing-handler.handle}'s own result -- that's reserved for
 * requests that are malformed before any network call is attempted (e.g. a
 * missing authority).
 * <br>
 * Depends on {@code "wasi-io"} ({@link WasiIoResources}): request/response
 * bodies are registered in the same shared input/output-stream tables
 * {@code wasi:io/streams} operates on, and every pollable this context hands
 * out comes from the same shared table {@code wasi:io/poll} reads.
 * <br>
 * Divergences from the full spec, documented rather than worked around:
 * {@code request-options#between-bytes-timeout} has no {@link HttpClient}
 * equivalent and is stored but not applied; {@code outgoing-request.headers()}
 * (and the {@code outgoing-response} equivalent) return the same mutable
 * {@code fields} table entry passed to the constructor rather than a
 * separately-immutable view, since guests build headers before constructing
 * the request and only read them back afterward in practice.
 */
public class WasiHttpContext implements TypesContext, OutgoingHandlerContext {

    /** The stable name other contexts reference via {@code getDependencies()}. */
    public static final String NAME = "wasi-http";

    private SemanticVersion version = new SemanticVersion(0, 2, 8);
    private WasiIoResources io;

    private final AtomicInteger nextRep = new AtomicInteger(1);

    private final Map<Integer, FieldsEntry> fields = new ConcurrentHashMap<>();
    private final Map<Integer, IncomingRequestEntry> incomingRequests = new ConcurrentHashMap<>();
    private final Map<Integer, OutgoingRequestEntry> outgoingRequests = new ConcurrentHashMap<>();
    private final Map<Integer, RequestOptionsEntry> requestOptions = new ConcurrentHashMap<>();
    private final Map<Integer, WitResult> responseOutparams = new ConcurrentHashMap<>();
    private final Map<Integer, IncomingResponseEntry> incomingResponses = new ConcurrentHashMap<>();
    private final Map<Integer, IncomingBodyEntry> incomingBodies = new ConcurrentHashMap<>();
    private final Set<Integer> futureTrailers = ConcurrentHashMap.newKeySet();
    private final Set<Integer> futureTrailersConsumed = ConcurrentHashMap.newKeySet();
    private final Map<Integer, OutgoingResponseEntry> outgoingResponses = new ConcurrentHashMap<>();
    private final Map<Integer, OutgoingBodyEntry> outgoingBodies = new ConcurrentHashMap<>();
    private final Map<Integer, WitResult> futureIncomingResponseResults = new ConcurrentHashMap<>();
    private final Set<Integer> futureIncomingResponseConsumed = ConcurrentHashMap.newKeySet();

    private static final class FieldsEntry {
        final List<Map.Entry<String, byte[]>> entries = new ArrayList<>();
        final boolean mutable;

        FieldsEntry(boolean mutable) {
            this.mutable = mutable;
        }
    }

    private static final class IncomingRequestEntry {
        volatile WitVariant method = new WitVariant("get", null);
        volatile Optional<String> pathWithQuery = Optional.empty();
        volatile Optional<WitVariant> scheme = Optional.empty();
        volatile Optional<String> authority = Optional.empty();
        final int headersRep;
        volatile InputStream bodyStream;
        volatile boolean consumed;

        IncomingRequestEntry(int headersRep) {
            this.headersRep = headersRep;
        }
    }

    private static final class OutgoingRequestEntry {
        volatile WitVariant method = new WitVariant("get", null);
        volatile Optional<String> pathWithQuery = Optional.empty();
        volatile Optional<WitVariant> scheme = Optional.empty();
        volatile Optional<String> authority = Optional.empty();
        final int headersRep;
        volatile boolean bodyTaken;
        volatile byte[] bodyBytes;

        OutgoingRequestEntry(int headersRep) {
            this.headersRep = headersRep;
        }
    }

    private static final class RequestOptionsEntry {
        volatile Long connectTimeoutNanos;
        volatile Long firstByteTimeoutNanos;
        volatile Long betweenBytesTimeoutNanos;
    }

    private static final class IncomingResponseEntry {
        final int status;
        final int headersRep;
        final InputStream bodyStream;
        volatile boolean bodyConsumed;

        IncomingResponseEntry(int status, int headersRep, InputStream bodyStream) {
            this.status = status;
            this.headersRep = headersRep;
            this.bodyStream = bodyStream;
        }
    }

    private static final class IncomingBodyEntry {
        final InputStream stream;
        volatile boolean streamTaken;

        IncomingBodyEntry(InputStream stream) {
            this.stream = stream;
        }
    }

    private static final class OutgoingResponseEntry {
        volatile int statusCode = 200;
        final int headersRep;
        volatile boolean bodyTaken;
        volatile byte[] bodyBytes;

        OutgoingResponseEntry(int headersRep) {
            this.headersRep = headersRep;
        }
    }

    private static final class OutgoingBodyEntry {
        volatile ByteArrayOutputStream buffer;
        volatile boolean streamTaken;
        Integer ownerRequestRep;
        Integer ownerResponseRep;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> getProvidedInterfaces() {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(TypesContext.super.getProvidedInterfaces());
        result.addAll(OutgoingHandlerContext.super.getProvidedInterfaces());
        return result;
    }

    @Override
    public List<String> getDependencies() {
        return List.of(WasiIoContext.NAME);
    }

    @Override
    public void onDependenciesResolved(ComponentContextLookup lookup) {
        this.io = (WasiIoResources) lookup.resolve(WasiIoContext.NAME, getVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "\"" + NAME + "\" requires a \"" + WasiIoContext.NAME + "\" dependency implementing "
                                + WasiIoResources.class.getSimpleName()));
    }

    @Override
    public List<ComponentImportFunction> getImportFunctions() {
        List<ComponentImportFunction> result = new ArrayList<>();
        result.addAll(TypesContext.super.getImportFunctions());
        result.addAll(OutgoingHandlerContext.super.getImportFunctions());
        return result;
    }

    @Override
    public List<ComponentImportResource> getImportResources() {
        List<ComponentImportResource> result = new ArrayList<>();
        result.addAll(TypesContext.super.getImportResources());
        result.addAll(OutgoingHandlerContext.super.getImportResources());
        return result;
    }

    // ---- resource destructors ------------------------------------------------

    @Override
    public void dropFields(int rep) {
        fields.remove(rep);
    }

    @Override
    public void dropIncomingRequest(int rep) {
        incomingRequests.remove(rep);
    }

    @Override
    public void dropOutgoingRequest(int rep) {
        outgoingRequests.remove(rep);
    }

    @Override
    public void dropRequestOptions(int rep) {
        requestOptions.remove(rep);
    }

    @Override
    public void dropResponseOutparam(int rep) {
        responseOutparams.remove(rep);
    }

    @Override
    public void dropIncomingResponse(int rep) {
        IncomingResponseEntry entry = incomingResponses.remove(rep);
        if (entry != null && !entry.bodyConsumed) {
            closeQuietly(entry.bodyStream);
        }
    }

    @Override
    public void dropIncomingBody(int rep) {
        IncomingBodyEntry entry = incomingBodies.remove(rep);
        if (entry != null && !entry.streamTaken) {
            closeQuietly(entry.stream);
        }
    }

    @Override
    public void dropFutureTrailers(int rep) {
        futureTrailers.remove(rep);
        futureTrailersConsumed.remove(rep);
    }

    @Override
    public void dropOutgoingResponse(int rep) {
        outgoingResponses.remove(rep);
    }

    @Override
    public void dropOutgoingBody(int rep) {
        outgoingBodies.remove(rep);
    }

    @Override
    public void dropFutureIncomingResponse(int rep) {
        futureIncomingResponseResults.remove(rep);
        futureIncomingResponseConsumed.remove(rep);
    }

    /**
     * {@code io-error} is a plain alias for {@code wasi:io/error}'s own
     * {@code error} resource (hence the canonical destructor name {@code
     * dropError}, not {@code dropIoError}) -- no real instance is ever
     * constructed with actual failure content (see {@link #typesHttpErrorCode}),
     * so there is nothing to release, matching {@link WasiIoContext#dropError}.
     */
    @Override
    public void dropError(int rep) {
        // Nothing to release, see javadoc.
    }

    @Override
    public void dropInputStream(int rep) {
        io.dropInputStream(rep);
    }

    @Override
    public void dropOutputStream(int rep) {
        io.dropOutputStream(rep);
    }

    @Override
    public void dropPollable(int rep) {
        io.dropPollable(rep);
    }

    private static void closeQuietly(InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                // Best-effort close, mirrors WasiIoContext#dropOutputStream.
            }
        }
    }

    // ---- http-error-code -------------------------------------------------------

    /**
     * No real {@code io-error} resource is ever constructed with actual
     * failure content in this implementation, so there's never anything
     * http-related to extract -- matches {@code WasiIoContext#errorToDebugString}'s
     * "nothing structured to report" stance.
     */
    @Override
    public Optional<Object> typesHttpErrorCode(WasmtimeComponentInstance instance, WitResource err) {
        return Optional.empty();
    }

    // ---- fields ----------------------------------------------------------------

    private static boolean sameName(String a, String b) {
        return a.equalsIgnoreCase(b);
    }

    private int registerFields(List<Map.Entry<String, byte[]>> initialEntries, boolean mutable) {
        FieldsEntry entry = new FieldsEntry(mutable);
        entry.entries.addAll(initialEntries);
        int rep = nextRep.getAndIncrement();
        fields.put(rep, entry);
        return rep;
    }

    @Override
    public WitResource fields(WasmtimeComponentInstance instance) {
        return WitResource.own("fields", registerFields(List.of(), true));
    }

    @SuppressWarnings("unchecked")
    @Override
    public WitResult fieldsFromList(WasmtimeComponentInstance instance, List<Object> entries) {
        List<Map.Entry<String, byte[]>> converted = new ArrayList<>();
        for (Object item : entries) {
            Object[] tuple = (Object[]) item;
            String name = (String) tuple[0];
            byte[] value = (byte[]) tuple[1];
            if (name.isEmpty() || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
                return WitResult.err(new WitVariant("invalid-syntax", null));
            }
            converted.add(Map.entry(name, value));
        }
        return WitResult.ok(WitResource.own("fields", registerFields(converted, true)));
    }

    @Override
    public List<Object> fieldsGet(WasmtimeComponentInstance instance, WitResource self, String name) {
        FieldsEntry entry = fields.get(self.rep());
        if (entry == null) {
            return List.of();
        }
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : entry.entries) {
            if (sameName(e.getKey(), name)) {
                values.add(e.getValue());
            }
        }
        return values;
    }

    @Override
    public boolean fieldsHas(WasmtimeComponentInstance instance, WitResource self, String name) {
        FieldsEntry entry = fields.get(self.rep());
        if (entry == null) {
            return false;
        }
        return entry.entries.stream().anyMatch(e -> sameName(e.getKey(), name));
    }

    @Override
    public WitResult fieldsSet(WasmtimeComponentInstance instance, WitResource self, String name,
            List<Object> value) {
        FieldsEntry entry = fields.get(self.rep());
        if (entry == null) {
            return WitResult.err(new WitVariant("invalid-syntax", null));
        }
        if (!entry.mutable) {
            return WitResult.err(new WitVariant("immutable", null));
        }
        entry.entries.removeIf(e -> sameName(e.getKey(), name));
        for (Object v : value) {
            entry.entries.add(Map.entry(name, (byte[]) v));
        }
        return WitResult.ok(null);
    }

    @Override
    public WitResult fieldsDelete(WasmtimeComponentInstance instance, WitResource self, String name) {
        FieldsEntry entry = fields.get(self.rep());
        if (entry == null) {
            return WitResult.err(new WitVariant("invalid-syntax", null));
        }
        if (!entry.mutable) {
            return WitResult.err(new WitVariant("immutable", null));
        }
        entry.entries.removeIf(e -> sameName(e.getKey(), name));
        return WitResult.ok(null);
    }

    @Override
    public WitResult fieldsAppend(WasmtimeComponentInstance instance, WitResource self, String name, byte[] value) {
        FieldsEntry entry = fields.get(self.rep());
        if (entry == null) {
            return WitResult.err(new WitVariant("invalid-syntax", null));
        }
        if (!entry.mutable) {
            return WitResult.err(new WitVariant("immutable", null));
        }
        entry.entries.add(Map.entry(name, value));
        return WitResult.ok(null);
    }

    @Override
    public List<Object> fieldsEntries(WasmtimeComponentInstance instance, WitResource self) {
        FieldsEntry entry = fields.get(self.rep());
        if (entry == null) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : entry.entries) {
            result.add(new Object[] { e.getKey(), e.getValue() });
        }
        return result;
    }

    @Override
    public WitResource fieldsClone(WasmtimeComponentInstance instance, WitResource self) {
        FieldsEntry entry = fields.get(self.rep());
        List<Map.Entry<String, byte[]>> copy = entry == null ? List.of() : new ArrayList<>(entry.entries);
        return WitResource.own("fields", registerFields(copy, true));
    }

    // ---- incoming-request (reserved for a future incoming-handler context) ----

    @Override
    public WitVariant incomingRequestMethod(WasmtimeComponentInstance instance, WitResource self) {
        IncomingRequestEntry entry = incomingRequests.get(self.rep());
        return entry == null ? new WitVariant("get", null) : entry.method;
    }

    @Override
    public Optional<Object> incomingRequestPathWithQuery(WasmtimeComponentInstance instance, WitResource self) {
        IncomingRequestEntry entry = incomingRequests.get(self.rep());
        return entry == null ? Optional.empty() : entry.pathWithQuery.map(Object.class::cast);
    }

    @Override
    public Optional<Object> incomingRequestScheme(WasmtimeComponentInstance instance, WitResource self) {
        IncomingRequestEntry entry = incomingRequests.get(self.rep());
        return entry == null ? Optional.empty() : entry.scheme.map(Object.class::cast);
    }

    @Override
    public Optional<Object> incomingRequestAuthority(WasmtimeComponentInstance instance, WitResource self) {
        IncomingRequestEntry entry = incomingRequests.get(self.rep());
        return entry == null ? Optional.empty() : entry.authority.map(Object.class::cast);
    }

    @Override
    public WitResource incomingRequestHeaders(WasmtimeComponentInstance instance, WitResource self) {
        IncomingRequestEntry entry = incomingRequests.get(self.rep());
        return WitResource.borrow("fields", entry == null ? -1 : entry.headersRep);
    }

    @Override
    public WitResult incomingRequestConsume(WasmtimeComponentInstance instance, WitResource self) {
        IncomingRequestEntry entry = incomingRequests.get(self.rep());
        if (entry == null || entry.consumed) {
            return WitResult.err(null);
        }
        entry.consumed = true;
        int bodyRep = nextRep.getAndIncrement();
        incomingBodies.put(bodyRep, new IncomingBodyEntry(entry.bodyStream));
        return WitResult.ok(WitResource.own("incoming-body", bodyRep));
    }

    // ---- outgoing-request --------------------------------------------------

    @Override
    public WitResource outgoingRequest(WasmtimeComponentInstance instance, WitResource headers) {
        int rep = nextRep.getAndIncrement();
        outgoingRequests.put(rep, new OutgoingRequestEntry(headers.rep()));
        return WitResource.own("outgoing-request", rep);
    }

    @Override
    public WitResult outgoingRequestBody(WasmtimeComponentInstance instance, WitResource self) {
        OutgoingRequestEntry entry = outgoingRequests.get(self.rep());
        if (entry == null || entry.bodyTaken) {
            return WitResult.err(null);
        }
        entry.bodyTaken = true;
        OutgoingBodyEntry body = new OutgoingBodyEntry();
        body.ownerRequestRep = self.rep();
        int bodyRep = nextRep.getAndIncrement();
        outgoingBodies.put(bodyRep, body);
        return WitResult.ok(WitResource.own("outgoing-body", bodyRep));
    }

    @Override
    public WitVariant outgoingRequestMethod(WasmtimeComponentInstance instance, WitResource self) {
        OutgoingRequestEntry entry = outgoingRequests.get(self.rep());
        return entry == null ? new WitVariant("get", null) : entry.method;
    }

    @Override
    public WitResult outgoingRequestSetMethod(WasmtimeComponentInstance instance, WitResource self,
            WitVariant method) {
        OutgoingRequestEntry entry = outgoingRequests.get(self.rep());
        if (entry == null) {
            return WitResult.err(null);
        }
        entry.method = method;
        return WitResult.ok(null);
    }

    @Override
    public Optional<Object> outgoingRequestPathWithQuery(WasmtimeComponentInstance instance, WitResource self) {
        OutgoingRequestEntry entry = outgoingRequests.get(self.rep());
        return entry == null ? Optional.empty() : entry.pathWithQuery.map(Object.class::cast);
    }

    @SuppressWarnings("unchecked")
    @Override
    public WitResult outgoingRequestSetPathWithQuery(WasmtimeComponentInstance instance, WitResource self,
            Optional<Object> pathWithQuery) {
        OutgoingRequestEntry entry = outgoingRequests.get(self.rep());
        if (entry == null) {
            return WitResult.err(null);
        }
        entry.pathWithQuery = (Optional<String>) (Optional<?>) pathWithQuery;
        return WitResult.ok(null);
    }

    @Override
    public Optional<Object> outgoingRequestScheme(WasmtimeComponentInstance instance, WitResource self) {
        OutgoingRequestEntry entry = outgoingRequests.get(self.rep());
        return entry == null ? Optional.empty() : entry.scheme.map(Object.class::cast);
    }

    @SuppressWarnings("unchecked")
    @Override
    public WitResult outgoingRequestSetScheme(WasmtimeComponentInstance instance, WitResource self,
            Optional<Object> scheme) {
        OutgoingRequestEntry entry = outgoingRequests.get(self.rep());
        if (entry == null) {
            return WitResult.err(null);
        }
        entry.scheme = (Optional<WitVariant>) (Optional<?>) scheme;
        return WitResult.ok(null);
    }

    @Override
    public Optional<Object> outgoingRequestAuthority(WasmtimeComponentInstance instance, WitResource self) {
        OutgoingRequestEntry entry = outgoingRequests.get(self.rep());
        return entry == null ? Optional.empty() : entry.authority.map(Object.class::cast);
    }

    @SuppressWarnings("unchecked")
    @Override
    public WitResult outgoingRequestSetAuthority(WasmtimeComponentInstance instance, WitResource self,
            Optional<Object> authority) {
        OutgoingRequestEntry entry = outgoingRequests.get(self.rep());
        if (entry == null) {
            return WitResult.err(null);
        }
        entry.authority = (Optional<String>) (Optional<?>) authority;
        return WitResult.ok(null);
    }

    /**
     * Returns the same mutable {@code fields} table entry passed to the
     * constructor rather than a separately-immutable view -- see class
     * javadoc.
     */
    @Override
    public WitResource outgoingRequestHeaders(WasmtimeComponentInstance instance, WitResource self) {
        OutgoingRequestEntry entry = outgoingRequests.get(self.rep());
        return WitResource.borrow("fields", entry == null ? -1 : entry.headersRep);
    }

    // ---- request-options --------------------------------------------------

    @Override
    public WitResource requestOptions(WasmtimeComponentInstance instance) {
        int rep = nextRep.getAndIncrement();
        requestOptions.put(rep, new RequestOptionsEntry());
        return WitResource.own("request-options", rep);
    }

    @Override
    public Optional<Object> requestOptionsConnectTimeout(WasmtimeComponentInstance instance, WitResource self) {
        RequestOptionsEntry entry = requestOptions.get(self.rep());
        return entry == null || entry.connectTimeoutNanos == null
                ? Optional.empty()
                : Optional.of(entry.connectTimeoutNanos);
    }

    @Override
    public WitResult requestOptionsSetConnectTimeout(WasmtimeComponentInstance instance, WitResource self,
            Optional<Object> duration) {
        RequestOptionsEntry entry = requestOptions.get(self.rep());
        if (entry == null) {
            return WitResult.err(null);
        }
        entry.connectTimeoutNanos = (Long) duration.orElse(null);
        return WitResult.ok(null);
    }

    @Override
    public Optional<Object> requestOptionsFirstByteTimeout(WasmtimeComponentInstance instance, WitResource self) {
        RequestOptionsEntry entry = requestOptions.get(self.rep());
        return entry == null || entry.firstByteTimeoutNanos == null
                ? Optional.empty()
                : Optional.of(entry.firstByteTimeoutNanos);
    }

    @Override
    public WitResult requestOptionsSetFirstByteTimeout(WasmtimeComponentInstance instance, WitResource self,
            Optional<Object> duration) {
        RequestOptionsEntry entry = requestOptions.get(self.rep());
        if (entry == null) {
            return WitResult.err(null);
        }
        entry.firstByteTimeoutNanos = (Long) duration.orElse(null);
        return WitResult.ok(null);
    }

    @Override
    public Optional<Object> requestOptionsBetweenBytesTimeout(WasmtimeComponentInstance instance, WitResource self) {
        RequestOptionsEntry entry = requestOptions.get(self.rep());
        return entry == null || entry.betweenBytesTimeoutNanos == null
                ? Optional.empty()
                : Optional.of(entry.betweenBytesTimeoutNanos);
    }

    /**
     * Stored but not applied -- {@link HttpClient} has no equivalent, see class
     * javadoc.
     */
    @Override
    public WitResult requestOptionsSetBetweenBytesTimeout(WasmtimeComponentInstance instance, WitResource self,
            Optional<Object> duration) {
        RequestOptionsEntry entry = requestOptions.get(self.rep());
        if (entry == null) {
            return WitResult.err(null);
        }
        entry.betweenBytesTimeoutNanos = (Long) duration.orElse(null);
        return WitResult.ok(null);
    }

    // ---- response-outparam (reserved for a future incoming-handler context) ---

    @Override
    public void responseOutparamSet(WasmtimeComponentInstance instance, WitResource param, WitResult response) {
        responseOutparams.put(param.rep(), response);
    }

    // ---- incoming-response / incoming-body ---------------------------------

    @Override
    public int incomingResponseStatus(WasmtimeComponentInstance instance, WitResource self) {
        IncomingResponseEntry entry = incomingResponses.get(self.rep());
        return entry == null ? 0 : entry.status;
    }

    @Override
    public WitResource incomingResponseHeaders(WasmtimeComponentInstance instance, WitResource self) {
        IncomingResponseEntry entry = incomingResponses.get(self.rep());
        return WitResource.borrow("fields", entry == null ? -1 : entry.headersRep);
    }

    @Override
    public WitResult incomingResponseConsume(WasmtimeComponentInstance instance, WitResource self) {
        IncomingResponseEntry entry = incomingResponses.get(self.rep());
        if (entry == null || entry.bodyConsumed) {
            return WitResult.err(null);
        }
        entry.bodyConsumed = true;
        int bodyRep = nextRep.getAndIncrement();
        incomingBodies.put(bodyRep, new IncomingBodyEntry(entry.bodyStream));
        return WitResult.ok(WitResource.own("incoming-body", bodyRep));
    }

    @Override
    public WitResult incomingBodyStream(WasmtimeComponentInstance instance, WitResource self) {
        IncomingBodyEntry entry = incomingBodies.get(self.rep());
        if (entry == null || entry.streamTaken) {
            return WitResult.err(null);
        }
        entry.streamTaken = true;
        int streamRep = io.registerInputStream(entry.stream);
        return WitResult.ok(WitResource.own("input-stream", streamRep));
    }

    /**
     * No real trailers are ever delivered (see {@link #futureTrailersGet}), so
     * this only needs to hand out an identity for the resulting
     * {@code future-trailers}.
     */
    @Override
    public WitResource incomingBodyFinish(WasmtimeComponentInstance instance, WitResource this_) {
        incomingBodies.remove(this_.rep());
        int trailersRep = nextRep.getAndIncrement();
        futureTrailers.add(trailersRep);
        return WitResource.own("future-trailers", trailersRep);
    }

    @Override
    public WitResource futureTrailersSubscribe(WasmtimeComponentInstance instance, WitResource self) {
        int rep = io.registerPollableDeadline(WasiIoResources.ALWAYS_READY);
        return WitResource.own("pollable", rep);
    }

    /**
     * Always resolves to "no trailers" -- no real trailers are ever collected,
     * see class javadoc.
     */
    @Override
    public Optional<Object> futureTrailersGet(WasmtimeComponentInstance instance, WitResource self) {
        if (!futureTrailers.contains(self.rep())) {
            return Optional.empty();
        }
        if (!futureTrailersConsumed.add(self.rep())) {
            return Optional.of(WitResult.err(null));
        }
        return Optional.of(WitResult.ok(WitResult.ok(Optional.empty())));
    }

    // ---- outgoing-response (reserved for a future incoming-handler context) ---

    @Override
    public WitResource outgoingResponse(WasmtimeComponentInstance instance, WitResource headers) {
        int rep = nextRep.getAndIncrement();
        outgoingResponses.put(rep, new OutgoingResponseEntry(headers.rep()));
        return WitResource.own("outgoing-response", rep);
    }

    @Override
    public int outgoingResponseStatusCode(WasmtimeComponentInstance instance, WitResource self) {
        OutgoingResponseEntry entry = outgoingResponses.get(self.rep());
        return entry == null ? 200 : entry.statusCode;
    }

    @Override
    public WitResult outgoingResponseSetStatusCode(WasmtimeComponentInstance instance, WitResource self,
            int statusCode) {
        OutgoingResponseEntry entry = outgoingResponses.get(self.rep());
        if (entry == null) {
            return WitResult.err(null);
        }
        entry.statusCode = statusCode;
        return WitResult.ok(null);
    }

    @Override
    public WitResource outgoingResponseHeaders(WasmtimeComponentInstance instance, WitResource self) {
        OutgoingResponseEntry entry = outgoingResponses.get(self.rep());
        return WitResource.borrow("fields", entry == null ? -1 : entry.headersRep);
    }

    @Override
    public WitResult outgoingResponseBody(WasmtimeComponentInstance instance, WitResource self) {
        OutgoingResponseEntry entry = outgoingResponses.get(self.rep());
        if (entry == null || entry.bodyTaken) {
            return WitResult.err(null);
        }
        entry.bodyTaken = true;
        OutgoingBodyEntry body = new OutgoingBodyEntry();
        body.ownerResponseRep = self.rep();
        int bodyRep = nextRep.getAndIncrement();
        outgoingBodies.put(bodyRep, body);
        return WitResult.ok(WitResource.own("outgoing-body", bodyRep));
    }

    // ---- outgoing-body ------------------------------------------------------

    @Override
    public WitResult outgoingBodyWrite(WasmtimeComponentInstance instance, WitResource self) {
        OutgoingBodyEntry body = outgoingBodies.get(self.rep());
        if (body == null || body.streamTaken) {
            return WitResult.err(null);
        }
        body.streamTaken = true;
        body.buffer = new ByteArrayOutputStream();
        int streamRep = io.registerOutputStream(body.buffer);
        return WitResult.ok(WitResource.own("output-stream", streamRep));
    }

    @Override
    public WitResult outgoingBodyFinish(WasmtimeComponentInstance instance, WitResource this_,
            Optional<Object> trailers) {
        OutgoingBodyEntry body = outgoingBodies.remove(this_.rep());
        if (body == null) {
            return WitResult.err(new WitVariant("internal-error", Optional.of("unknown outgoing-body")));
        }
        byte[] bytes = body.buffer != null ? body.buffer.toByteArray() : new byte[0];
        if (body.ownerRequestRep != null) {
            OutgoingRequestEntry request = outgoingRequests.get(body.ownerRequestRep);
            if (request != null) {
                request.bodyBytes = bytes;
            }
        }
        if (body.ownerResponseRep != null) {
            OutgoingResponseEntry response = outgoingResponses.get(body.ownerResponseRep);
            if (response != null) {
                response.bodyBytes = bytes;
            }
        }
        return WitResult.ok(null);
    }

    // ---- outgoing-handler ---------------------------------------------------

    private static String methodToHttpString(WitVariant method) {
        return switch (method.caseName()) {
            case "get" -> "GET";
            case "head" -> "HEAD";
            case "post" -> "POST";
            case "put" -> "PUT";
            case "delete" -> "DELETE";
            case "connect" -> "CONNECT";
            case "options" -> "OPTIONS";
            case "trace" -> "TRACE";
            case "patch" -> "PATCH";
            case "other" -> (String) method.value();
            default -> "GET";
        };
    }

    private static String schemeToHttpString(WitVariant scheme) {
        return switch (scheme.caseName()) {
            case "HTTP" -> "http";
            case "HTTPS" -> "https";
            case "other" -> (String) scheme.value();
            default -> "https";
        };
    }

    private List<Map.Entry<String, byte[]>> fieldEntries(int headersRep) {
        FieldsEntry entry = fields.get(headersRep);
        return entry == null ? List.of() : entry.entries;
    }

    private static List<Map.Entry<String, byte[]>> toFieldEntries(HttpHeaders headers) {
        List<Map.Entry<String, byte[]>> result = new ArrayList<>();
        headers.map().forEach((name, values) -> {
            for (String value : values) {
                result.add(Map.entry(name, value.getBytes(StandardCharsets.ISO_8859_1)));
            }
        });
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public WitResult outgoingHandlerHandle(WasmtimeComponentInstance instance, WitResource request,
            Optional<Object> options) {
        OutgoingRequestEntry entry = outgoingRequests.get(request.rep());
        if (entry == null) {
            return WitResult.err(new WitVariant("internal-error", Optional.of("unknown outgoing-request")));
        }
        if (entry.authority.isEmpty()) {
            return WitResult.err(new WitVariant("HTTP-request-URI-invalid", null));
        }

        String schemeStr = entry.scheme.map(WasiHttpContext::schemeToHttpString).orElse("https");
        String pathAndQuery = entry.pathWithQuery.orElse("");
        if (pathAndQuery.isEmpty() || !pathAndQuery.startsWith("/")) {
            pathAndQuery = "/" + pathAndQuery;
        }
        URI uri;
        try {
            uri = new URI(schemeStr + "://" + entry.authority.get() + pathAndQuery);
        } catch (URISyntaxException e) {
            return WitResult.err(new WitVariant("HTTP-request-URI-invalid", null));
        }

        RequestOptionsEntry opts = options.isPresent()
                ? requestOptions.get(((WitResource) options.get()).rep())
                : null;

        HttpClient.Builder clientBuilder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER);
        if (opts != null && opts.connectTimeoutNanos != null) {
            clientBuilder.connectTimeout(Duration.ofNanos(opts.connectTimeoutNanos));
        }
        HttpClient client = clientBuilder.build();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri);
        if (opts != null && opts.firstByteTimeoutNanos != null) {
            requestBuilder.timeout(Duration.ofNanos(opts.firstByteTimeoutNanos));
        }
        for (Map.Entry<String, byte[]> header : fieldEntries(entry.headersRep)) {
            try {
                requestBuilder.header(header.getKey(), new String(header.getValue(), StandardCharsets.ISO_8859_1));
            } catch (IllegalArgumentException e) {
                // Restricted/invalid header for java.net.http (e.g. "Host"), skip it.
            }
        }
        byte[] body = entry.bodyBytes != null ? entry.bodyBytes : new byte[0];
        requestBuilder.method(methodToHttpString(entry.method), HttpRequest.BodyPublishers.ofByteArray(body));

        int futureRep = nextRep.getAndIncrement();
        try {
            HttpResponse<InputStream> response = client.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            int headersRep = registerFields(toFieldEntries(response.headers()), false);
            int responseRep = nextRep.getAndIncrement();
            incomingResponses.put(responseRep,
                    new IncomingResponseEntry(response.statusCode(), headersRep, response.body()));
            futureIncomingResponseResults.put(futureRep,
                    WitResult.ok(WitResource.own("incoming-response", responseRep)));
        } catch (ConnectException e) {
            futureIncomingResponseResults.put(futureRep, WitResult.err(new WitVariant("connection-refused", null)));
        } catch (HttpTimeoutException e) {
            futureIncomingResponseResults.put(futureRep, WitResult.err(new WitVariant("connection-timeout", null)));
        } catch (UnknownHostException e) {
            futureIncomingResponseResults.put(futureRep,
                    WitResult.err(new WitVariant("destination-not-found", null)));
        } catch (IOException e) {
            futureIncomingResponseResults.put(futureRep,
                    WitResult.err(new WitVariant("internal-error", Optional.ofNullable(e.getMessage()))));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            futureIncomingResponseResults.put(futureRep,
                    WitResult.err(new WitVariant("internal-error", Optional.of("interrupted"))));
        }
        return WitResult.ok(WitResource.own("future-incoming-response", futureRep));
    }

    @Override
    public WitResource futureIncomingResponseSubscribe(WasmtimeComponentInstance instance, WitResource self) {
        int rep = io.registerPollableDeadline(WasiIoResources.ALWAYS_READY);
        return WitResource.own("pollable", rep);
    }

    @Override
    public Optional<Object> futureIncomingResponseGet(WasmtimeComponentInstance instance, WitResource self) {
        WitResult result = futureIncomingResponseResults.get(self.rep());
        if (result == null) {
            return Optional.empty();
        }
        if (!futureIncomingResponseConsumed.add(self.rep())) {
            return Optional.of(WitResult.err(null));
        }
        return Optional.of(WitResult.ok(result));
    }

    @Override
    public WasiHttpContext withVersion(SemanticVersion version) {
        if (!supportsVersion(version)) {
            throw new IllegalArgumentException("Unsupported version: " + version);
        }
        this.version = version;
        return this;
    }

    @Override
    public SemanticVersion getVersion() {
        return this.version;
    }

    @Override
    public SemanticVersion getMiniumVersion() {
        return new SemanticVersion(0, 2, 0);
    }

    @Override
    public SemanticVersion getMaximumVersion() {
        return new SemanticVersion(0, 3, 0);
    }
}
