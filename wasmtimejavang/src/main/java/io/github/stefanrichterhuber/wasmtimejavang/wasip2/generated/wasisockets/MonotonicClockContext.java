package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasisockets;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:clocks/monotonic-clock" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface MonotonicClockContext extends WasmComponentContext {
    String INTERFACE = "wasi:clocks/monotonic-clock";

    @Override
    default String name() {
        return "monotonic-clock";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "now", this::monotonicClockNowImpl),
                new ComponentImportFunction(versioned(), "resolution", this::monotonicClockResolutionImpl),
                new ComponentImportFunction(versioned(), "subscribe-instant", this::monotonicClockSubscribeInstantImpl),
                new ComponentImportFunction(versioned(), "subscribe-duration", this::monotonicClockSubscribeDurationImpl)
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

    long monotonicClockNow(WasmtimeComponentInstance instance);

    long monotonicClockResolution(WasmtimeComponentInstance instance);

    WitResource monotonicClockSubscribeInstant(WasmtimeComponentInstance instance, long when);

    WitResource monotonicClockSubscribeDuration(WasmtimeComponentInstance instance, long when);

    void dropPollable(int rep);

    private Object[] monotonicClockNowImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { monotonicClockNow(instance) };
    }

    private Object[] monotonicClockResolutionImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { monotonicClockResolution(instance) };
    }

    private Object[] monotonicClockSubscribeInstantImpl(WasmtimeComponentInstance instance, Object... args) {
        long when = (Long) args[0];
        return new Object[] { monotonicClockSubscribeInstant(instance, when) };
    }

    private Object[] monotonicClockSubscribeDurationImpl(WasmtimeComponentInstance instance, Object... args) {
        long when = (Long) args[0];
        return new Object[] { monotonicClockSubscribeDuration(instance, when) };
    }

}
