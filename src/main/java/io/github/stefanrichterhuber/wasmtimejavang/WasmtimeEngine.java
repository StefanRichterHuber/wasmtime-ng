package io.github.stefanrichterhuber.wasmtimejavang;

import io.questdb.jar.jni.JarJniLoader;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents a Wasmtime engine.
 * The engine is a global context for WebAssembly compilation and execution.
 * It handles the loading of the native Wasmtime binding and coordinates
 * logging between the native code and Java.
 */
public final class WasmtimeEngine implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Logger NATIVE_LOGGER = LogManager.getLogger("[Wasmtime native library]");

    /**
     * Initializes the logging for the native library. Only allowed to be called
     * once!
     * 
     * @param level Log level from 0 (off) to 5 (trace)
     */
    private static native void initLogging(int level);

    // Loads the native library and initializes logging
    static {
        // Load the native library
        JarJniLoader.loadLib(
                WasmtimeEngine.class,
                // A platform-specific path is automatically suffixed to path below.
                "/io/github/stefanrichterhuber/wasmtimejavang/libs",
                // The "lib" prefix and ".so|.dynlib|.dll" suffix are added automatically as
                // needed.
                "wasmtimebinding");

        // Initialize native logging. This is only possible once, otherwise the rust log
        // library fails
        if (NATIVE_LOGGER.getLevel() == Level.ERROR || LOGGER.getLevel() == Level.FATAL) {
            initLogging(1);
        } else if (NATIVE_LOGGER.getLevel() == Level.WARN) {
            initLogging(2);
        } else if (NATIVE_LOGGER.getLevel() == Level.INFO) {
            initLogging(3);
        } else if (NATIVE_LOGGER.getLevel() == Level.DEBUG) {
            initLogging(4);
        } else if (NATIVE_LOGGER.getLevel() == Level.TRACE) {
            initLogging(5);
        } else if (NATIVE_LOGGER.getLevel() == Level.OFF) {
            initLogging(0);
        } else {
            LOGGER.warn("Unknown log level " + NATIVE_LOGGER.getLevel() + " , using INFO for native library");
            initLogging(3);
        }
    }

    /**
     * This method is called by the native code to log a message.
     * 
     * @param level   Log level
     * @param message Message to log
     */
    static void runtimeLog(int level, String message) {
        final Level logLevel = switch (level) {
            case 5 -> Level.TRACE;
            case 4 -> Level.DEBUG;
            case 3 -> Level.INFO;
            case 2 -> Level.WARN;
            case 1 -> Level.ERROR;
            case 0 -> Level.FATAL;
            default -> null;
        };
        NATIVE_LOGGER.log(logLevel, message);
    }

    private long enginePtr;

    private native long createEngine();

    private native void closeEngine(long enginePtr);

    /**
     * Creates a new WasmtimeEngine.
     */
    public WasmtimeEngine() {
        this.enginePtr = createEngine();
    }

    /**
     * Returns the native pointer to the engine.
     * 
     * @return The native engine pointer.
     */
    long getEnginePtr() {
        return this.enginePtr;
    }

    /**
     * Closes the engine and releases native resources.
     */
    @Override
    public void close() throws Exception {
        if (enginePtr != 0) {
            this.closeEngine(this.enginePtr);
        }
        enginePtr = 0;
    }
}
