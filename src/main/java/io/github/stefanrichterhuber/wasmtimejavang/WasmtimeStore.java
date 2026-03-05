package io.github.stefanrichterhuber.wasmtimejavang;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WasmtimeStore implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger();

    private long storePtr;

    private final WasmtimeEngine engine;

    private native long createStore(long enginePtr);

    private native void closeStore(long storePtr);

    @Override
    public void close() throws Exception {
        if (storePtr != 0) {
            this.closeStore(storePtr);
        }
        storePtr = 0;
    }

    long getStorePtr() {
        return this.storePtr;
    }

    public WasmtimeStore(WasmtimeEngine engine) {
        this.engine = engine;
        this.storePtr = createStore(engine.getEnginePtr());
    }
}
