package io.github.stefanrichterhuber.wasmtimejavang;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a compiled WebAssembly component (e.g. a {@code wasm32-wasip2}
 * binary), as opposed to a core WebAssembly {@link WasmtimeModule}.
 * Once compiled, it can be instantiated multiple times via a
 * {@link WasmtimeComponentLinker} / {@link WasmtimeComponentInstance}.
 */
public final class WasmtimeComponent implements AutoCloseable {

    private long componentPtr;

    private final WasmtimeEngine engine;

    private native long createComponent(long enginePtr, ByteBuffer source);

    private native static void closeComponent(long componentPtr);

    private native Object[] importInterfaces(long componentPtr);

    private native Object[] exportInterfaces(long componentPtr);

    private final Cleaner.Cleanable cleanable;

    private static class CleanState implements Runnable {
        private final long componentPtr;

        CleanState(long componentPtr) {
            this.componentPtr = componentPtr;
        }

        @Override
        public void run() {
            WasmtimeComponent.closeComponent(componentPtr);
        }
    }

    /**
     * Compiles a WebAssembly component from a binary buffer.
     *
     * @param engine Engine to use for compilation.
     * @param source ByteBuffer containing the component binary. If the
     *               ByteBuffer is direct, it can be passed to the native
     *               context without copying.
     */
    public WasmtimeComponent(WasmtimeEngine engine, ByteBuffer source) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        Objects.requireNonNull(source, "source must not be null");
        this.componentPtr = createComponent(engine.getEnginePtr(), WasmtimeModule.createByteBuffer(source));
        this.cleanable = WasmtimeEngine.CLEANER.register(this, new CleanState(this.componentPtr));
    }

    /**
     * Compiles a WebAssembly component from a byte array.
     *
     * @param engine Engine to use for compilation.
     * @param source byte array containing the component binary.
     */
    public WasmtimeComponent(WasmtimeEngine engine, byte[] source) {
        this(engine, WasmtimeModule.createByteBuffer(Objects.requireNonNull(source, "source must not be null")));
    }

    /**
     * Compiles a WebAssembly component from an InputStream.
     *
     * @param engine Engine to use for compilation.
     * @param is     InputStream containing the component binary.
     * @throws IOException If reading from the stream fails.
     */
    public WasmtimeComponent(WasmtimeEngine engine, InputStream is) throws IOException {
        this(engine, WasmtimeModule.createByteBuffer(Objects.requireNonNull(is, "is must not be null")));
    }

    /**
     * Returns the native pointer to the component.
     *
     * @return The native component pointer.
     */
    long getComponentPtr() {
        if (componentPtr == 0) {
            throw new IllegalStateException("Component no longer active");
        }
        return this.componentPtr;
    }

    /**
     * Closes the component and releases native resources.
     */
    @Override
    public void close() throws Exception {
        if (componentPtr != 0) {
            this.cleanable.clean();
        }
        componentPtr = 0;
    }

    /**
     * Returns the WasmtimeEngine associated with this component.
     *
     * @return The WasmtimeEngine instance.
     */
    public WasmtimeEngine getEngine() {
        return this.engine;
    }

    /**
     * Returns the names of this component's top-level named interface
     * imports (e.g. {@code "wasi:io/poll@0.2.6"}), read directly from the
     * compiled component via wasmtime -- no instantiation or linking
     * required. Bare root-level function imports (not associated with a
     * named interface) are not included.
     *
     * @return The names of the interfaces this component needs linked in
     *         order to be instantiated.
     */
    public List<String> getImportInterfaces() {
        return toStringList(importInterfaces(getComponentPtr()));
    }

    /**
     * Returns the names of this component's top-level named interface
     * exports (e.g. {@code "wasi:cli/run@0.2.6"}), read directly from the
     * compiled component via wasmtime. Bare root-level function exports (not
     * associated with a named interface) are not included.
     *
     * @return The names of the interfaces this component exports.
     */
    public List<String> getExportInterfaces() {
        return toStringList(exportInterfaces(getComponentPtr()));
    }

    /**
     * Returns whether this component exports {@code wasi:cli/run} -- the
     * WASI Preview 2 convention for a component that can be run as a command
     * (i.e. compiled from a {@code fn main()} program, as opposed to e.g. a
     * {@code wasi:http/incoming-handler} service with no {@code run} entry
     * point).
     *
     * @return {@code true} if this component exports an interface whose name
     *         starts with {@code "wasi:cli/run@"}.
     */
    public boolean isCommand() {
        return getExportInterfaces().stream().anyMatch(name -> name.startsWith("wasi:cli/run@"));
    }

    private static List<String> toStringList(Object[] values) {
        return Arrays.stream(values).map(String.class::cast).collect(Collectors.toUnmodifiableList());
    }
}
