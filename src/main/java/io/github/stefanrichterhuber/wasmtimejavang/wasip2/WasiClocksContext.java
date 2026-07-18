package io.github.stefanrichterhuber.wasmtimejavang.wasip2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup;
import io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;

/**
 * Implementation of {@code wasi:clocks/monotonic-clock} and
 * {@code wasi:clocks/wall-clock} (WASI Preview 2, 0.2.6) -- the
 * {@code "wasi-clocks"} component context.
 * <br>
 * Depends on {@code "wasi-io"} ({@link WasiIoResources}) because the
 * {@code pollable} resource monotonic-clock's {@code subscribe-instant}/
 * {@code subscribe-duration} hand out is owned by {@code wasi:io/poll}: any
 * pollable minted here must come from the same table {@code wasi:io/poll}'s
 * {@code [method]pollable.block} reads from.
 */
public class WasiClocksContext implements WasmComponentContext {

    /** The stable name other contexts reference via {@code getDependencies()}. */
    public static final String NAME = "wasi-clocks";

    private static final String WASI_CLOCKS_MONOTONIC = "wasi:clocks/monotonic-clock";
    private static final String WASI_CLOCKS_WALL = "wasi:clocks/wall-clock";
    private static final Set<String> PROVIDED_INTERFACES = Set.of(WASI_CLOCKS_MONOTONIC, WASI_CLOCKS_WALL);

    private WasiIoResources io;
    private SemanticVersion version = WasiCliContext.DEFAULT_VERSION;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> getProvidedInterfaces() {
        return PROVIDED_INTERFACES;
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
        return List.of(
                new ComponentImportFunction(WASI_CLOCKS_MONOTONIC + "@" + version, "now", this::monotonicNow),
                new ComponentImportFunction(WASI_CLOCKS_MONOTONIC + "@" + version, "subscribe-instant",
                        this::subscribeInstant),
                new ComponentImportFunction(WASI_CLOCKS_MONOTONIC + "@" + version, "subscribe-duration",
                        this::subscribeDuration),
                new ComponentImportFunction(WASI_CLOCKS_WALL + "@" + version, "now", this::wallClockNow));
    }

    @Override
    public List<ComponentImportResource> getImportResources() {
        return List
                .of(new ComponentImportResource(WASI_CLOCKS_MONOTONIC + "@" + version, "pollable", io::dropPollable));
    }

    protected Object[] monotonicNow(WasmtimeComponentInstance instance, Object[] args) {
        return new Object[] { System.nanoTime() };
    }

    protected Object[] subscribeInstant(WasmtimeComponentInstance instance, Object[] args) {
        long when = (Long) args[0];
        int rep = io.registerPollableDeadline(when);
        return new Object[] { WitResource.own("pollable", rep) };
    }

    protected Object[] subscribeDuration(WasmtimeComponentInstance instance, Object[] args) {
        long durationNanos = (Long) args[0];
        int rep = io.registerPollableDeadline(System.nanoTime() + durationNanos);
        return new Object[] { WitResource.own("pollable", rep) };
    }

    protected Object[] wallClockNow(WasmtimeComponentInstance instance, Object[] args) {
        long millis = System.currentTimeMillis();
        Map<String, Object> datetime = new LinkedHashMap<>();
        datetime.put("seconds", millis / 1000L);
        datetime.put("nanoseconds", (int) ((millis % 1000L) * 1_000_000L));
        return new Object[] { datetime };
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
