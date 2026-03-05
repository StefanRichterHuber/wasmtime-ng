package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class WasmtimeLinker implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger();
    private long linkerPtr;

    private final WasmtimeEngine engine;

    private final WasmtimeStore store;

    private native long createLinker(long enginePtr);

    private native void closeLinker(long linkerPtr);

    private native void defineFunction(long enginePtr, long storePtr, WasmFunction func, String name,
            List<ValType> params,
            List<ValType> returnTypes);

    @Override
    public void close() throws Exception {
        if (linkerPtr != 0) {
            this.closeLinker(linkerPtr);
        }
        linkerPtr = 0;
    }

    long getLinkerPtr() {
        return this.linkerPtr;
    }

    public WasmtimeLinker(WasmtimeEngine engine, WasmtimeStore store) {
        this.engine = engine;
        this.store = store;
        this.linkerPtr = createLinker(engine.getEnginePtr());
    }

    public void importFunction(String name, List<ValType> parameters, List<ValType> returnTypes, WasmFunction f) {
        defineFunction(this.engine.getEnginePtr(), this.store.getStorePtr(), f, name, parameters, returnTypes);
    }
}
