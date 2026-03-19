package io.github.stefanrichterhuber.wasmtimejavang;

import java.lang.ref.Cleaner;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents a Wasmtime store.
 * A store is a container for all WebAssembly state, including instances,
 * memories, and globals.
 * It also maintains a context map that is passed to Java-implemented functions.
 */
public final class WasmtimeStore implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger();

    private long storePtr;

    private final WasmtimeEngine engine;

    private native long createStore(long enginePtr, Map<String, Object> context);

    private native static void closeStore(long storePtr);

    private final Cleaner.Cleanable cleanable;

    private static class CleanState implements Runnable {
        private final long storePtr;

        CleanState(long storePtr) {
            this.storePtr = storePtr;
        }

        @Override
        public void run() {
            WasmtimeStore.closeStore(storePtr);
        }
    }

    /**
     * Global context. Thread-safe
     */
    private final Map<String, Object> context;

    /**
     * Closes the store and releases native resources.
     */
    @Override
    public void close() throws Exception {
        if (storePtr != 0) {
            this.cleanable.clean();
        }
        storePtr = 0;
    }

    /**
     * Returns the native pointer to the store.
     * 
     * @return The native store pointer.
     */
    long getStorePtr() {
        if (storePtr == 0) {
            throw new IllegalStateException("Store no longer active");
        }
        return this.storePtr;
    }

    /**
     * Creates a new WasmtimeStore.
     * 
     * @param engine  The engine associated with this store.
     * @param context Mutable Map to use as context. When wasm threads is used, this
     *                map must be thread-safe!
     */
    public WasmtimeStore(WasmtimeEngine engine, Map<String, Object> context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.storePtr = createStore(engine.getEnginePtr(), this.context);
        this.cleanable = WasmtimeEngine.CLEANER.register(this, new CleanState(this.storePtr));
    }

    /**
     * Creates a new WasmtimeStore.
     * 
     * @param engine The engine associated with this store.
     */
    public WasmtimeStore(WasmtimeEngine engine) {
        this(engine, new ConcurrentHashMap<>());
    }

    /**
     * Returns the context map associated with this store.
     * This map is passed to all Java functions called from WASM.
     * 
     * @return The mutable context map.
     */
    public Map<String, Object> getContext() {
        return this.context;
    }

    /**
     * Returns the WasmtimeEngine associated with this store.
     * 
     * @return The WasmtimeEngine instance.
     */
    public WasmtimeEngine getEngine() {
        return this.engine;
    }

    public WasmtimeStore createClone() {
        return new WasmtimeStore(this.engine, this.context);
    }
}
