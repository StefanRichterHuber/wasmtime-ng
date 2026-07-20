package io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiclocks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup;
import io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasiclocks.MonotonicClockContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasiclocks.WallClockContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiio.WasiIoContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiio.WasiIoResources;

/**
 * Implementation of {@code wasi:clocks/monotonic-clock} and
 * {@code wasi:clocks/wall-clock} (WASI Preview 2, 0.2.6) -- the
 * {@code "wasi-clocks"} component context.
 * <br>
 * Implements both generated interfaces at once (see {@link WasiRandomContext}
 * for why this works and how {@code getImportFunctions()}/
 * {@code getImportResources()}/{@code getProvidedInterfaces()} get combined).
 * <br>
 * Depends on {@code "wasi-io"} ({@link WasiIoResources}) because the
 * {@code pollable} resource monotonic-clock's {@code subscribe-instant}/
 * {@code subscribe-duration} hand out is owned by {@code wasi:io/poll}: any
 * pollable minted here must come from the same table {@code wasi:io/poll}'s
 * {@code [method]pollable.block} reads from.
 */
public class WasiClocksContext implements MonotonicClockContext, WallClockContext {

    /** The stable name other contexts reference via {@code getDependencies()}. */
    public static final String NAME = "wasi-clocks";

    private WasiIoResources io;
    private SemanticVersion version = DEFAULT_VERSION;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> getProvidedInterfaces() {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(MonotonicClockContext.super.getProvidedInterfaces());
        result.addAll(WallClockContext.super.getProvidedInterfaces());
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
        result.addAll(MonotonicClockContext.super.getImportFunctions());
        result.addAll(WallClockContext.super.getImportFunctions());
        return result;
    }

    @Override
    public List<ComponentImportResource> getImportResources() {
        List<ComponentImportResource> result = new ArrayList<>();
        result.addAll(MonotonicClockContext.super.getImportResources());
        result.addAll(WallClockContext.super.getImportResources());
        return result;
    }

    @Override
    public long monotonicClockNow(WasmtimeComponentInstance instance) {
        return System.nanoTime();
    }

    /**
     * {@code System.nanoTime()} claims nanosecond precision, so 1 is reported
     * as the clock's resolution.
     */
    @Override
    public long monotonicClockResolution(WasmtimeComponentInstance instance) {
        return 1L;
    }

    @Override
    public WitResource monotonicClockSubscribeInstant(WasmtimeComponentInstance instance, long when) {
        int rep = io.registerPollableDeadline(when);
        return WitResource.own("pollable", rep);
    }

    @Override
    public WitResource monotonicClockSubscribeDuration(WasmtimeComponentInstance instance, long when) {
        int rep = io.registerPollableDeadline(System.nanoTime() + when);
        return WitResource.own("pollable", rep);
    }

    @Override
    public void dropPollable(int rep) {
        io.dropPollable(rep);
    }

    @Override
    public Map<String, Object> wallClockNow(WasmtimeComponentInstance instance) {
        long millis = System.currentTimeMillis();
        Map<String, Object> datetime = new LinkedHashMap<>();
        datetime.put("seconds", millis / 1000L);
        datetime.put("nanoseconds", (int) ((millis % 1000L) * 1_000_000L));
        return datetime;
    }

    /**
     * {@code System.currentTimeMillis()} backs {@link #wallClockNow}, so its
     * actual granularity (1ms) is reported as the clock's resolution.
     */
    @Override
    public Map<String, Object> wallClockResolution(WasmtimeComponentInstance instance) {
        Map<String, Object> datetime = new LinkedHashMap<>();
        datetime.put("seconds", 0L);
        datetime.put("nanoseconds", 1_000_000);
        return datetime;
    }

    @Override
    public WasiClocksContext withVersion(SemanticVersion version) {
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
        return new SemanticVersion(0, 0, 1);
    }

    @Override
    public SemanticVersion getMaximumVersion() {
        return new SemanticVersion(0, 3, 0);
    }
}
