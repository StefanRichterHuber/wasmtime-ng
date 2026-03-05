package io.github.stefanrichterhuber.wasmtimejavang;

import io.questdb.jar.jni.JarJniLoader;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class WasmtimeEngine implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Logger NATIVE_LOGGER = LogManager.getLogger("[QuickJS native library]");

    /**
     * Initializes the logging for the native library. Only allowed to be called
     * once!
     * 
     * @param level Log level from 0 (off) to 5 (trace)
     */
    private static void initLogging(int level) {
        // TODO: change to native
    }

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
        switch (level) {
            case 0:
                // DO nothing -> log is off
                break;
            case 5:
                NATIVE_LOGGER.trace(message);
                break;
            case 4:
                NATIVE_LOGGER.debug(message);
                break;
            case 3:
                NATIVE_LOGGER.info(message);
                break;
            case 2:
                NATIVE_LOGGER.warn(message);
                break;
            case 1:
                NATIVE_LOGGER.error(message);
                break;
            default:
                NATIVE_LOGGER.error(message);
        }
    }

    private long enginePtr;

    private native long createEngine();

    private native void closeEngine(long enginePtr);

    public WasmtimeEngine() {
        this.enginePtr = createEngine();
    }

    long getEnginePtr() {
        return this.enginePtr;
    }

    @Override
    public void close() throws Exception {
        if (enginePtr != 0) {
            this.closeEngine(this.enginePtr);
        }
        enginePtr = 0;
    }

    public WasmtimeModule createModule(ByteBuffer src) {
        return new WasmtimeModule(this, src);
    }

    public WasmtimeModule createModule(String src) {
        return new WasmtimeModule(this, src);
    }

}
