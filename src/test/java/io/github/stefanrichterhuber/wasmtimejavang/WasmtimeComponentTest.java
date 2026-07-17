package io.github.stefanrichterhuber.wasmtimejavang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext.ComponentImportFunction;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext.ComponentImportResource;

/**
 * Milestone A verification: proves the generic Component Model plumbing
 * (WasmtimeComponent / WasmtimeComponentLinker / WasmtimeComponentInstance
 * and the Rust-side Val &lt;-&gt; Java Object bridge) round-trips a value
 * through a real component, end to end, without any WASI involved.
 */
public class WasmtimeComponentTest {

    /**
     * A minimal hand-written component: imports a "is-even(u32) -> bool"
     * function (implemented in Java below) and re-exports it as "run" so the
     * test can drive it with real Java-supplied arguments and observe a real
     * Java-implemented import's return value flow back out.
     */
    private static final String COMPONENT_WAT = """
            (component
                (import "is-even" (func $is-even (param "x" u32) (result bool)))

                (core module $m
                    (import "" "is-even" (func $is-even (param i32) (result i32)))
                    (func (export "run") (param i32) (result i32)
                        local.get 0
                        call $is-even
                    )
                )
                (core func $is-even-lower (canon lower (func $is-even)))
                (core instance $i (instantiate $m
                    (with "" (instance
                        (export "is-even" (func $is-even-lower))
                    ))
                ))

                (func (export "run") (param "x" u32) (result bool) (canon lift (core func $i "run")))
            )
            """;

    @Test
    public void roundTripsAJavaImplementedImport() throws Exception {
        try (
                WasmtimeEngine engine = new WasmtimeEngine();
                WasmtimeComponent component = new WasmtimeComponent(engine,
                        COMPONENT_WAT.getBytes(StandardCharsets.UTF_8));
                WasmtimeStore store = new WasmtimeStore(engine);
                WasmtimeComponentLinker linker = new WasmtimeComponentLinker(engine, store)) {

            linker.linkContext(new WasmComponentContext() {
                @Override
                public List<ComponentImportFunction> getImportFunctions() {
                    return List.of(new ComponentImportFunction("", "is-even", (instance, args) -> {
                        int x = (Integer) args[0];
                        return new Object[] { x % 2 == 0 };
                    }));
                }

                @Override
                public List<ComponentImportResource> getImportResources() {
                    return List.of();
                }

                @Override
                public String name() {
                    return "test-context";
                }

                @Override
                public WasmComponentContext withVersion(SemanticVersion version) {
                    return this;
                }

                @Override
                public SemanticVersion getVersion() {
                    return SemanticVersion.parse("0.3.0");
                }

                @Override
                public SemanticVersion getMiniumVersion() {
                    return SemanticVersion.parse("0.0.1");
                }

                @Override
                public SemanticVersion getMaximumVersion() {
                    return SemanticVersion.parse("0.3.0");
                }
            });

            try (WasmtimeComponentInstance instance = new WasmtimeComponentInstance(store, component, linker)) {
                Object[] resultEven = instance.invoke("", "run", 4);
                assertEquals(Boolean.TRUE, resultEven[0]);

                Object[] resultOdd = instance.invoke("", "run", 5);
                assertEquals(Boolean.FALSE, resultOdd[0]);
            }
        }
    }
}
