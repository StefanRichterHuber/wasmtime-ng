package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasihttp;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitVariant;
import java.util.Optional;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:http/types" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface TypesContext extends WasmComponentContext {
    String INTERFACE = "wasi:http/types";

    @Override
    default String name() {
        return "types";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "http-error-code", this::typesHttpErrorCodeImpl),
                new ComponentImportFunction(versioned(), "[constructor]fields", this::fieldsImpl),
                new ComponentImportFunction(versioned(), "[static]fields.from-list", this::fieldsFromListImpl),
                new ComponentImportFunction(versioned(), "[method]fields.get", this::fieldsGetImpl),
                new ComponentImportFunction(versioned(), "[method]fields.has", this::fieldsHasImpl),
                new ComponentImportFunction(versioned(), "[method]fields.set", this::fieldsSetImpl),
                new ComponentImportFunction(versioned(), "[method]fields.delete", this::fieldsDeleteImpl),
                new ComponentImportFunction(versioned(), "[method]fields.append", this::fieldsAppendImpl),
                new ComponentImportFunction(versioned(), "[method]fields.entries", this::fieldsEntriesImpl),
                new ComponentImportFunction(versioned(), "[method]fields.clone", this::fieldsCloneImpl),
                new ComponentImportFunction(versioned(), "[method]incoming-request.method", this::incomingRequestMethodImpl),
                new ComponentImportFunction(versioned(), "[method]incoming-request.path-with-query", this::incomingRequestPathWithQueryImpl),
                new ComponentImportFunction(versioned(), "[method]incoming-request.scheme", this::incomingRequestSchemeImpl),
                new ComponentImportFunction(versioned(), "[method]incoming-request.authority", this::incomingRequestAuthorityImpl),
                new ComponentImportFunction(versioned(), "[method]incoming-request.headers", this::incomingRequestHeadersImpl),
                new ComponentImportFunction(versioned(), "[method]incoming-request.consume", this::incomingRequestConsumeImpl),
                new ComponentImportFunction(versioned(), "[constructor]outgoing-request", this::outgoingRequestImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-request.body", this::outgoingRequestBodyImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-request.method", this::outgoingRequestMethodImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-request.set-method", this::outgoingRequestSetMethodImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-request.path-with-query", this::outgoingRequestPathWithQueryImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-request.set-path-with-query", this::outgoingRequestSetPathWithQueryImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-request.scheme", this::outgoingRequestSchemeImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-request.set-scheme", this::outgoingRequestSetSchemeImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-request.authority", this::outgoingRequestAuthorityImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-request.set-authority", this::outgoingRequestSetAuthorityImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-request.headers", this::outgoingRequestHeadersImpl),
                new ComponentImportFunction(versioned(), "[constructor]request-options", this::requestOptionsImpl),
                new ComponentImportFunction(versioned(), "[method]request-options.connect-timeout", this::requestOptionsConnectTimeoutImpl),
                new ComponentImportFunction(versioned(), "[method]request-options.set-connect-timeout", this::requestOptionsSetConnectTimeoutImpl),
                new ComponentImportFunction(versioned(), "[method]request-options.first-byte-timeout", this::requestOptionsFirstByteTimeoutImpl),
                new ComponentImportFunction(versioned(), "[method]request-options.set-first-byte-timeout", this::requestOptionsSetFirstByteTimeoutImpl),
                new ComponentImportFunction(versioned(), "[method]request-options.between-bytes-timeout", this::requestOptionsBetweenBytesTimeoutImpl),
                new ComponentImportFunction(versioned(), "[method]request-options.set-between-bytes-timeout", this::requestOptionsSetBetweenBytesTimeoutImpl),
                new ComponentImportFunction(versioned(), "[static]response-outparam.set", this::responseOutparamSetImpl),
                new ComponentImportFunction(versioned(), "[method]incoming-response.status", this::incomingResponseStatusImpl),
                new ComponentImportFunction(versioned(), "[method]incoming-response.headers", this::incomingResponseHeadersImpl),
                new ComponentImportFunction(versioned(), "[method]incoming-response.consume", this::incomingResponseConsumeImpl),
                new ComponentImportFunction(versioned(), "[method]incoming-body.stream", this::incomingBodyStreamImpl),
                new ComponentImportFunction(versioned(), "[static]incoming-body.finish", this::incomingBodyFinishImpl),
                new ComponentImportFunction(versioned(), "[method]future-trailers.subscribe", this::futureTrailersSubscribeImpl),
                new ComponentImportFunction(versioned(), "[method]future-trailers.get", this::futureTrailersGetImpl),
                new ComponentImportFunction(versioned(), "[constructor]outgoing-response", this::outgoingResponseImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-response.status-code", this::outgoingResponseStatusCodeImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-response.set-status-code", this::outgoingResponseSetStatusCodeImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-response.headers", this::outgoingResponseHeadersImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-response.body", this::outgoingResponseBodyImpl),
                new ComponentImportFunction(versioned(), "[method]outgoing-body.write", this::outgoingBodyWriteImpl),
                new ComponentImportFunction(versioned(), "[static]outgoing-body.finish", this::outgoingBodyFinishImpl),
                new ComponentImportFunction(versioned(), "[method]future-incoming-response.subscribe", this::futureIncomingResponseSubscribeImpl),
                new ComponentImportFunction(versioned(), "[method]future-incoming-response.get", this::futureIncomingResponseGetImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "fields", this::dropFields),
                new ComponentImportResource(versioned(), "incoming-request", this::dropIncomingRequest),
                new ComponentImportResource(versioned(), "outgoing-request", this::dropOutgoingRequest),
                new ComponentImportResource(versioned(), "request-options", this::dropRequestOptions),
                new ComponentImportResource(versioned(), "response-outparam", this::dropResponseOutparam),
                new ComponentImportResource(versioned(), "incoming-response", this::dropIncomingResponse),
                new ComponentImportResource(versioned(), "incoming-body", this::dropIncomingBody),
                new ComponentImportResource(versioned(), "future-trailers", this::dropFutureTrailers),
                new ComponentImportResource(versioned(), "outgoing-response", this::dropOutgoingResponse),
                new ComponentImportResource(versioned(), "outgoing-body", this::dropOutgoingBody),
                new ComponentImportResource(versioned(), "future-incoming-response", this::dropFutureIncomingResponse),
                new ComponentImportResource(versioned(), "error", this::dropError),
                new ComponentImportResource(versioned(), "input-stream", this::dropInputStream),
                new ComponentImportResource(versioned(), "pollable", this::dropPollable),
                new ComponentImportResource(versioned(), "output-stream", this::dropOutputStream)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    Optional<Object> typesHttpErrorCode(WasmtimeComponentInstance instance, WitResource err);

    WitResource fields(WasmtimeComponentInstance instance);

    WitResult fieldsFromList(WasmtimeComponentInstance instance, List<Object> entries);

    List<Object> fieldsGet(WasmtimeComponentInstance instance, WitResource self, String name);

    boolean fieldsHas(WasmtimeComponentInstance instance, WitResource self, String name);

    WitResult fieldsSet(WasmtimeComponentInstance instance, WitResource self, String name, List<Object> value);

    WitResult fieldsDelete(WasmtimeComponentInstance instance, WitResource self, String name);

    WitResult fieldsAppend(WasmtimeComponentInstance instance, WitResource self, String name, byte[] value);

    List<Object> fieldsEntries(WasmtimeComponentInstance instance, WitResource self);

    WitResource fieldsClone(WasmtimeComponentInstance instance, WitResource self);

    WitVariant incomingRequestMethod(WasmtimeComponentInstance instance, WitResource self);

    Optional<Object> incomingRequestPathWithQuery(WasmtimeComponentInstance instance, WitResource self);

    Optional<Object> incomingRequestScheme(WasmtimeComponentInstance instance, WitResource self);

    Optional<Object> incomingRequestAuthority(WasmtimeComponentInstance instance, WitResource self);

    WitResource incomingRequestHeaders(WasmtimeComponentInstance instance, WitResource self);

    WitResult incomingRequestConsume(WasmtimeComponentInstance instance, WitResource self);

    WitResource outgoingRequest(WasmtimeComponentInstance instance, WitResource headers);

    WitResult outgoingRequestBody(WasmtimeComponentInstance instance, WitResource self);

    WitVariant outgoingRequestMethod(WasmtimeComponentInstance instance, WitResource self);

    WitResult outgoingRequestSetMethod(WasmtimeComponentInstance instance, WitResource self, WitVariant method);

    Optional<Object> outgoingRequestPathWithQuery(WasmtimeComponentInstance instance, WitResource self);

    WitResult outgoingRequestSetPathWithQuery(WasmtimeComponentInstance instance, WitResource self, Optional<Object> pathWithQuery);

    Optional<Object> outgoingRequestScheme(WasmtimeComponentInstance instance, WitResource self);

    WitResult outgoingRequestSetScheme(WasmtimeComponentInstance instance, WitResource self, Optional<Object> scheme);

    Optional<Object> outgoingRequestAuthority(WasmtimeComponentInstance instance, WitResource self);

    WitResult outgoingRequestSetAuthority(WasmtimeComponentInstance instance, WitResource self, Optional<Object> authority);

    WitResource outgoingRequestHeaders(WasmtimeComponentInstance instance, WitResource self);

    WitResource requestOptions(WasmtimeComponentInstance instance);

    Optional<Object> requestOptionsConnectTimeout(WasmtimeComponentInstance instance, WitResource self);

    WitResult requestOptionsSetConnectTimeout(WasmtimeComponentInstance instance, WitResource self, Optional<Object> duration);

    Optional<Object> requestOptionsFirstByteTimeout(WasmtimeComponentInstance instance, WitResource self);

    WitResult requestOptionsSetFirstByteTimeout(WasmtimeComponentInstance instance, WitResource self, Optional<Object> duration);

    Optional<Object> requestOptionsBetweenBytesTimeout(WasmtimeComponentInstance instance, WitResource self);

    WitResult requestOptionsSetBetweenBytesTimeout(WasmtimeComponentInstance instance, WitResource self, Optional<Object> duration);

    void responseOutparamSet(WasmtimeComponentInstance instance, WitResource param, WitResult response);

    int incomingResponseStatus(WasmtimeComponentInstance instance, WitResource self);

    WitResource incomingResponseHeaders(WasmtimeComponentInstance instance, WitResource self);

    WitResult incomingResponseConsume(WasmtimeComponentInstance instance, WitResource self);

    WitResult incomingBodyStream(WasmtimeComponentInstance instance, WitResource self);

    WitResource incomingBodyFinish(WasmtimeComponentInstance instance, WitResource this_);

    WitResource futureTrailersSubscribe(WasmtimeComponentInstance instance, WitResource self);

    Optional<Object> futureTrailersGet(WasmtimeComponentInstance instance, WitResource self);

    WitResource outgoingResponse(WasmtimeComponentInstance instance, WitResource headers);

    int outgoingResponseStatusCode(WasmtimeComponentInstance instance, WitResource self);

    WitResult outgoingResponseSetStatusCode(WasmtimeComponentInstance instance, WitResource self, int statusCode);

    WitResource outgoingResponseHeaders(WasmtimeComponentInstance instance, WitResource self);

    WitResult outgoingResponseBody(WasmtimeComponentInstance instance, WitResource self);

    WitResult outgoingBodyWrite(WasmtimeComponentInstance instance, WitResource self);

    WitResult outgoingBodyFinish(WasmtimeComponentInstance instance, WitResource this_, Optional<Object> trailers);

    WitResource futureIncomingResponseSubscribe(WasmtimeComponentInstance instance, WitResource self);

    Optional<Object> futureIncomingResponseGet(WasmtimeComponentInstance instance, WitResource self);

    void dropFields(int rep);

    void dropIncomingRequest(int rep);

    void dropOutgoingRequest(int rep);

    void dropRequestOptions(int rep);

    void dropResponseOutparam(int rep);

    void dropIncomingResponse(int rep);

    void dropIncomingBody(int rep);

    void dropFutureTrailers(int rep);

    void dropOutgoingResponse(int rep);

    void dropOutgoingBody(int rep);

    void dropFutureIncomingResponse(int rep);

    void dropError(int rep);

    void dropInputStream(int rep);

    void dropPollable(int rep);

    void dropOutputStream(int rep);

    private Object[] typesHttpErrorCodeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource err = (WitResource) args[0];
        return new Object[] { typesHttpErrorCode(instance, err) };
    }

    private Object[] fieldsImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { fields(instance) };
    }

    private Object[] fieldsFromListImpl(WasmtimeComponentInstance instance, Object... args) {
        List<Object> entries = (List<Object>) args[0];
        return new Object[] { fieldsFromList(instance, entries) };
    }

    private Object[] fieldsGetImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        String name = (String) args[1];
        return new Object[] { fieldsGet(instance, self, name) };
    }

    private Object[] fieldsHasImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        String name = (String) args[1];
        return new Object[] { fieldsHas(instance, self, name) };
    }

    private Object[] fieldsSetImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        String name = (String) args[1];
        List<Object> value = (List<Object>) args[2];
        return new Object[] { fieldsSet(instance, self, name, value) };
    }

    private Object[] fieldsDeleteImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        String name = (String) args[1];
        return new Object[] { fieldsDelete(instance, self, name) };
    }

    private Object[] fieldsAppendImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        String name = (String) args[1];
        byte[] value = (byte[]) args[2];
        return new Object[] { fieldsAppend(instance, self, name, value) };
    }

    private Object[] fieldsEntriesImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { fieldsEntries(instance, self) };
    }

    private Object[] fieldsCloneImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { fieldsClone(instance, self) };
    }

    private Object[] incomingRequestMethodImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { incomingRequestMethod(instance, self) };
    }

    private Object[] incomingRequestPathWithQueryImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { incomingRequestPathWithQuery(instance, self) };
    }

    private Object[] incomingRequestSchemeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { incomingRequestScheme(instance, self) };
    }

    private Object[] incomingRequestAuthorityImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { incomingRequestAuthority(instance, self) };
    }

    private Object[] incomingRequestHeadersImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { incomingRequestHeaders(instance, self) };
    }

    private Object[] incomingRequestConsumeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { incomingRequestConsume(instance, self) };
    }

    private Object[] outgoingRequestImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource headers = (WitResource) args[0];
        return new Object[] { outgoingRequest(instance, headers) };
    }

    private Object[] outgoingRequestBodyImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outgoingRequestBody(instance, self) };
    }

    private Object[] outgoingRequestMethodImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outgoingRequestMethod(instance, self) };
    }

    private Object[] outgoingRequestSetMethodImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        WitVariant method = (WitVariant) args[1];
        return new Object[] { outgoingRequestSetMethod(instance, self, method) };
    }

    private Object[] outgoingRequestPathWithQueryImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outgoingRequestPathWithQuery(instance, self) };
    }

    private Object[] outgoingRequestSetPathWithQueryImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        Optional<Object> pathWithQuery = (Optional<Object>) args[1];
        return new Object[] { outgoingRequestSetPathWithQuery(instance, self, pathWithQuery) };
    }

    private Object[] outgoingRequestSchemeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outgoingRequestScheme(instance, self) };
    }

    private Object[] outgoingRequestSetSchemeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        Optional<Object> scheme = (Optional<Object>) args[1];
        return new Object[] { outgoingRequestSetScheme(instance, self, scheme) };
    }

    private Object[] outgoingRequestAuthorityImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outgoingRequestAuthority(instance, self) };
    }

    private Object[] outgoingRequestSetAuthorityImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        Optional<Object> authority = (Optional<Object>) args[1];
        return new Object[] { outgoingRequestSetAuthority(instance, self, authority) };
    }

    private Object[] outgoingRequestHeadersImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outgoingRequestHeaders(instance, self) };
    }

    private Object[] requestOptionsImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { requestOptions(instance) };
    }

    private Object[] requestOptionsConnectTimeoutImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { requestOptionsConnectTimeout(instance, self) };
    }

    private Object[] requestOptionsSetConnectTimeoutImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        Optional<Object> duration = (Optional<Object>) args[1];
        return new Object[] { requestOptionsSetConnectTimeout(instance, self, duration) };
    }

    private Object[] requestOptionsFirstByteTimeoutImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { requestOptionsFirstByteTimeout(instance, self) };
    }

    private Object[] requestOptionsSetFirstByteTimeoutImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        Optional<Object> duration = (Optional<Object>) args[1];
        return new Object[] { requestOptionsSetFirstByteTimeout(instance, self, duration) };
    }

    private Object[] requestOptionsBetweenBytesTimeoutImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { requestOptionsBetweenBytesTimeout(instance, self) };
    }

    private Object[] requestOptionsSetBetweenBytesTimeoutImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        Optional<Object> duration = (Optional<Object>) args[1];
        return new Object[] { requestOptionsSetBetweenBytesTimeout(instance, self, duration) };
    }

    private Object[] responseOutparamSetImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource param = (WitResource) args[0];
        WitResult response = (WitResult) args[1];
        responseOutparamSet(instance, param, response);
        return new Object[0];
    }

    private Object[] incomingResponseStatusImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { incomingResponseStatus(instance, self) };
    }

    private Object[] incomingResponseHeadersImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { incomingResponseHeaders(instance, self) };
    }

    private Object[] incomingResponseConsumeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { incomingResponseConsume(instance, self) };
    }

    private Object[] incomingBodyStreamImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { incomingBodyStream(instance, self) };
    }

    private Object[] incomingBodyFinishImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource this_ = (WitResource) args[0];
        return new Object[] { incomingBodyFinish(instance, this_) };
    }

    private Object[] futureTrailersSubscribeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { futureTrailersSubscribe(instance, self) };
    }

    private Object[] futureTrailersGetImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { futureTrailersGet(instance, self) };
    }

    private Object[] outgoingResponseImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource headers = (WitResource) args[0];
        return new Object[] { outgoingResponse(instance, headers) };
    }

    private Object[] outgoingResponseStatusCodeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outgoingResponseStatusCode(instance, self) };
    }

    private Object[] outgoingResponseSetStatusCodeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        int statusCode = (Integer) args[1];
        return new Object[] { outgoingResponseSetStatusCode(instance, self, statusCode) };
    }

    private Object[] outgoingResponseHeadersImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outgoingResponseHeaders(instance, self) };
    }

    private Object[] outgoingResponseBodyImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outgoingResponseBody(instance, self) };
    }

    private Object[] outgoingBodyWriteImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outgoingBodyWrite(instance, self) };
    }

    private Object[] outgoingBodyFinishImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource this_ = (WitResource) args[0];
        Optional<Object> trailers = (Optional<Object>) args[1];
        return new Object[] { outgoingBodyFinish(instance, this_, trailers) };
    }

    private Object[] futureIncomingResponseSubscribeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { futureIncomingResponseSubscribe(instance, self) };
    }

    private Object[] futureIncomingResponseGetImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { futureIncomingResponseGet(instance, self) };
    }

}
