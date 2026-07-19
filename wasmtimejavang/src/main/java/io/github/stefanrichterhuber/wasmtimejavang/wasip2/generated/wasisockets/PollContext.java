package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasisockets;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:io/poll" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface PollContext extends WasmComponentContext {
    String INTERFACE = "wasi:io/poll";

    @Override
    default String name() {
        return "poll";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "[method]pollable.ready", this::pollableReadyImpl),
                new ComponentImportFunction(versioned(), "[method]pollable.block", this::pollableBlockImpl),
                new ComponentImportFunction(versioned(), "poll", this::pollPollImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "pollable", this::dropPollable)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    boolean pollableReady(WasmtimeComponentInstance instance, WitResource self);

    void pollableBlock(WasmtimeComponentInstance instance, WitResource self);

    List<Object> pollPoll(WasmtimeComponentInstance instance, List<Object> in);

    void dropPollable(int rep);

    private Object[] pollableReadyImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { pollableReady(instance, self) };
    }

    private Object[] pollableBlockImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        pollableBlock(instance, self);
        return new Object[0];
    }

    private Object[] pollPollImpl(WasmtimeComponentInstance instance, Object... args) {
        List<Object> in = (List<Object>) args[0];
        return new Object[] { pollPoll(instance, in) };
    }

}
