package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.HashMap;
import java.util.Map;

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

    private native void closeStore(long storePtr);

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
            this.closeStore(storePtr);
        }
        storePtr = 0;
    }

    /**
     * Returns the native pointer to the store.
     * 
     * @return The native store pointer.
     */
    long getStorePtr() {
        return this.storePtr;
    }

    /**
     * Creates a new WasmtimeStore.
     * 
     * @param engine  The engine associated with this store.
     * @param context Mutable Map to use as context
     */
    public WasmtimeStore(WasmtimeEngine engine, Map<String, Object> context) {
        if (engine == null) {
            throw new NullPointerException("WasmtimeEngine must not be null");
        }
        this.context = context;
        this.engine = engine;
        this.storePtr = createStore(engine.getEnginePtr(), this.context);
    }

    /**
     * Creates a new WasmtimeStore.
     * 
     * @param engine The engine associated with this store.
     */
    public WasmtimeStore(WasmtimeEngine engine) {
        this(engine, new HashMap<>());
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
}
