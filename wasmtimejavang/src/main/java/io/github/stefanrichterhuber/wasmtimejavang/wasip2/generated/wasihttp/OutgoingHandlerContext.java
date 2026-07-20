package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasihttp;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import java.util.Optional;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:http/outgoing-handler" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface OutgoingHandlerContext extends WasmComponentContext {
    String INTERFACE = "wasi:http/outgoing-handler";

    @Override
    default String name() {
        return "outgoing-handler";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "handle", this::outgoingHandlerHandleImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "outgoing-request", this::dropOutgoingRequest),
                new ComponentImportResource(versioned(), "request-options", this::dropRequestOptions),
                new ComponentImportResource(versioned(), "future-incoming-response", this::dropFutureIncomingResponse)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    WitResult outgoingHandlerHandle(WasmtimeComponentInstance instance, WitResource request, Optional<Object> options);

    void dropOutgoingRequest(int rep);

    void dropRequestOptions(int rep);

    void dropFutureIncomingResponse(int rep);

    private Object[] outgoingHandlerHandleImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource request = (WitResource) args[0];
        Optional<Object> options = (Optional<Object>) args[1];
        return new Object[] { outgoingHandlerHandle(instance, request, options) };
    }

}
