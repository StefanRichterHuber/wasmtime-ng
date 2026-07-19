package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.List;
import java.util.Set;

/**
 * Example {@link WasmComponentContext} implementing a small, deliberately
 * non-WASI interface ({@code my:custom/greet}) that a Java application would
 * define itself to expose custom host functionality to a component -- the
 * same pattern the README's "Implementing your own component context"
 * section documents, verified end to end by
 * {@link WasmtimeCustomComponentTest}.
 */
public class GreetComponentContext implements WasmComponentContext {
    private static final String INTERFACE = "my:custom/greet";

    private SemanticVersion version = new SemanticVersion(1, 0, 0);

    @Override
    public String name() {
        return "greet";
    }

    @Override
    public List<ComponentImportFunction> getImportFunctions() {
        String versioned = INTERFACE + "@" + version;
        return List.of(
                new ComponentImportFunction(versioned, "hello", this::hello),
                new ComponentImportFunction(versioned, "add", this::add));
    }

    @Override
    public List<ComponentImportResource> getImportResources() {
        return List.of();
    }

    @Override
    public Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    private Object[] hello(WasmtimeComponentInstance instance, Object... args) {
        String name = (String) args[0];
        return new Object[] { "Hello, " + name + "!" };
    }

    private Object[] add(WasmtimeComponentInstance instance, Object... args) {
        int a = (Integer) args[0];
        int b = (Integer) args[1];
        return new Object[] { a + b };
    }

    @Override
    public WasmComponentContext withVersion(SemanticVersion version) {
        this.version = version;
        return this;
    }

    @Override
    public SemanticVersion getVersion() {
        return version;
    }
}
