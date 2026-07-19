package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli;

import java.util.List;
import java.util.Set;
import java.util.Map;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:clocks/wall-clock" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface WallClockContext extends WasmComponentContext {
    String INTERFACE = "wasi:clocks/wall-clock";

    @Override
    default String name() {
        return "wall-clock";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "now", this::wallClockNowImpl),
                new ComponentImportFunction(versioned(), "resolution", this::wallClockResolutionImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of();
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    Map<String, Object> wallClockNow(WasmtimeComponentInstance instance);

    Map<String, Object> wallClockResolution(WasmtimeComponentInstance instance);

    private Object[] wallClockNowImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { wallClockNow(instance) };
    }

    private Object[] wallClockResolutionImpl(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { wallClockResolution(instance) };
    }

}
