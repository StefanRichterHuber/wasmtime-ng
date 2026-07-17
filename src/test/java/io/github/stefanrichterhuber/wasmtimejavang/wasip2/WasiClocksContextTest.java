package io.github.stefanrichterhuber.wasmtimejavang.wasip2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext.ComponentImportFunction;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext.ComponentImportResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;

/**
 * Direct unit tests for {@link WasiClocksContext}, wiring its {@code "wasi-io"}
 * dependency by hand (a real {@link WasiIoContext}) instead of going through
 * {@code WasmtimeComponentLinker}, so every function/resource -- including
 * {@code subscribe-instant}, which {@code WasmtimeWasiP2Test}'s wasm fixtures
 * never happen to call (stable Rust std has no API that maps to it) -- gets
 * exercised.
 */
public class WasiClocksContextTest {

    private static WasiClocksContext newLinkedClocks(WasiIoContext io) {
        WasiClocksContext clocks = new WasiClocksContext();
        clocks.onDependenciesResolved(
                (name, version) -> WasiIoContext.NAME.equals(name) ? Optional.of(io) : Optional.empty());
        return clocks;
    }

    @Test
    public void nameProvidedInterfacesAndDependencies() {
        WasiClocksContext clocks = new WasiClocksContext();
        assertEquals("wasi-clocks", clocks.name());
        assertEquals(WasiClocksContext.NAME, clocks.name());
        assertTrue(clocks.getProvidedInterfaces().contains("wasi:clocks/monotonic-clock"));
        assertTrue(clocks.getProvidedInterfaces().contains("wasi:clocks/wall-clock"));
        assertEquals(List.of(WasiIoContext.NAME), clocks.getDependencies());
    }

    @Test
    public void onDependenciesResolvedThrowsWhenWasiIoIsMissing() {
        WasiClocksContext clocks = new WasiClocksContext();
        ComponentContextLookup emptyLookup = (name, version) -> Optional.empty();
        assertThrows(IllegalStateException.class, () -> clocks.onDependenciesResolved(emptyLookup));
    }

    @Test
    public void importFunctionsCoverEveryDeclaredMethod() {
        WasiClocksContext clocks = newLinkedClocks(new WasiIoContext());
        List<ComponentImportFunction> functions = clocks.getImportFunctions();
        String monotonic = "wasi:clocks/monotonic-clock@" + clocks.getVersion();
        String wall = "wasi:clocks/wall-clock@" + clocks.getVersion();

        assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals(monotonic)
                && f.funcName().equals("now")));
        assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals(monotonic)
                && f.funcName().equals("subscribe-instant")));
        assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals(monotonic)
                && f.funcName().equals("subscribe-duration")));
        assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals(wall)
                && f.funcName().equals("now")));
    }

    @Test
    public void importResourceDestructorDelegatesToWasiIo() {
        WasiIoContext io = new WasiIoContext();
        WasiClocksContext clocks = newLinkedClocks(io);

        List<ComponentImportResource> resources = clocks.getImportResources();
        assertEquals(1, resources.size());
        ComponentImportResource pollableResource = resources.get(0);
        assertEquals("wasi:clocks/monotonic-clock@" + clocks.getVersion(), pollableResource.interfaceName());
        assertEquals("pollable", pollableResource.resourceName());

        int rep = io.registerPollableDeadline(123L);
        assertNotNull(io.getPollableDeadline(rep));
        pollableResource.destructor().drop(rep);
        assertNull(io.getPollableDeadline(rep));
    }

    @Test
    public void monotonicNowReturnsSystemNanoTime() {
        WasiClocksContext clocks = newLinkedClocks(new WasiIoContext());
        long before = System.nanoTime();
        Object[] result = clocks.monotonicNow(null, new Object[0]);
        long after = System.nanoTime();

        long reported = (Long) result[0];
        assertTrue(reported >= before && reported <= after,
                "expected " + reported + " to be between " + before + " and " + after);
    }

    @Test
    public void subscribeInstantRegistersExactDeadlineInWasiIo() {
        WasiIoContext io = new WasiIoContext();
        WasiClocksContext clocks = newLinkedClocks(io);

        long instant = System.nanoTime() + 5_000_000_000L;
        Object[] result = clocks.subscribeInstant(null, new Object[] { instant });
        WitResource pollable = (WitResource) result[0];

        assertEquals("pollable", pollable.resourceName());
        assertTrue(pollable.owned());
        assertEquals(instant, io.getPollableDeadline(pollable.rep()));
    }

    @Test
    public void subscribeDurationRegistersNowPlusDurationInWasiIo() {
        WasiIoContext io = new WasiIoContext();
        WasiClocksContext clocks = newLinkedClocks(io);

        long durationNanos = 2_000_000_000L;
        long before = System.nanoTime();
        Object[] result = clocks.subscribeDuration(null, new Object[] { durationNanos });
        long after = System.nanoTime();
        WitResource pollable = (WitResource) result[0];

        long deadline = io.getPollableDeadline(pollable.rep());
        assertTrue(deadline >= before + durationNanos && deadline <= after + durationNanos,
                "expected deadline " + deadline + " to be roughly now+duration");
    }

    @Test
    public void wallClockNowReturnsPlausibleDatetimeRecord() {
        WasiClocksContext clocks = newLinkedClocks(new WasiIoContext());
        long beforeMillis = System.currentTimeMillis();
        Object[] result = clocks.wallClockNow(null, new Object[0]);
        long afterMillis = System.currentTimeMillis();

        @SuppressWarnings("unchecked")
        Map<String, Object> datetime = (Map<String, Object>) result[0];
        long seconds = (Long) datetime.get("seconds");
        int nanoseconds = (Integer) datetime.get("nanoseconds");

        assertTrue(seconds >= beforeMillis / 1000L && seconds <= afterMillis / 1000L + 1);
        assertTrue(nanoseconds >= 0 && nanoseconds < 1_000_000_000);
    }

    /**
     * Sanity check that {@link WasiClocksContext} really is a
     * {@link WasmComponentContext}.
     */
    @Test
    public void implementsWasmComponentContext() {
        assertTrue(WasmComponentContext.class.isAssignableFrom(WasiClocksContext.class));
    }
}
