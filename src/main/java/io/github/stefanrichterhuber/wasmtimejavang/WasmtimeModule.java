package io.github.stefanrichterhuber.wasmtimejavang;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents a compiled WebAssembly module.
 * A module is compiled from WebAssembly binary format or text format (WAT).
 * Once compiled, it can be instantiated multiple times.
 */
public final class WasmtimeModule implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger();

    private long modulePtr;

    private final WasmtimeEngine engine;

    private native long createModule(long enginePtr, ByteBuffer source);

    private native void closeModule(long modulePtr);

    /**
     * Compiles a WebAssembly module from a binary buffer.
     * 
     * @param engine Engine to use for compilation.
     * @param source ByteBuffer containing the WASM binary. If the ByteBuffer is
     *               direct, it can be passed to the native context without copying.
     */
    public WasmtimeModule(WasmtimeEngine engine, ByteBuffer source) {
        if (engine == null) {
            throw new NullPointerException("WasmtimeEngine must not be null");
        }
        if (source == null) {
            throw new NullPointerException("Source ByteBuffer must not be null");
        }
        if (!source.isDirect()) {
            LOGGER.info(
                    "Only direct ByteBuffers could be directly move to the runtime, all others have to be copied in to a direct ByteBuffer");

            final ByteBuffer direcByteBuffer = ByteBuffer.allocateDirect(source.capacity());
            direcByteBuffer.put(source);
            direcByteBuffer.flip();
            source = direcByteBuffer;
        }
        this.engine = engine;
        this.modulePtr = createModule(engine.getEnginePtr(), source);
    }

    /**
     * Compiles a WebAssembly module from an byte array.
     * 
     * @param engine Engine to use for compilation.
     * @param source byte array containing the WASM binary.
     */
    public WasmtimeModule(WasmtimeEngine engine, byte[] source) {
        this(engine, createByteBuffer(source));
    }

    /**
     * Compiles a WebAssembly module from an InputStream.
     * 
     * @param engine Engine to use for compilation.
     * @param is     InputStream containing the WASM binary or WAT text.
     * @throws IOException If reading from the stream fails.
     */
    public WasmtimeModule(WasmtimeEngine engine, InputStream is) throws IOException {
        this(engine, createByteBuffer(is));
    }

    /**
     * Compiles a WebAssembly module from a WAT string.
     * 
     * @param engine Engine to use for compilation.
     * @param wat    The WAT source string.
     */
    public WasmtimeModule(WasmtimeEngine engine, String wat) {
        this(engine, createByteBuffer(wat));
    }

    /**
     * Utility function to copy a string into a direct ByteBuffer.
     * 
     * @param src String to copy.
     * @return Created ByteBuffer.
     */
    private static ByteBuffer createByteBuffer(String src) {
        byte[] srcBytes = src.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocateDirect(srcBytes.length);
        buf.put(srcBytes);
        buf.flip();
        return buf;
    }

    /**
     * Utility function to copy a string into a direct ByteBuffer.
     * 
     * @param src byte array to copy
     * @return Created ByteBuffer.
     */
    private static ByteBuffer createByteBuffer(byte[] src) {
        ByteBuffer buf = ByteBuffer.allocateDirect(src.length);
        buf.put(src);
        buf.flip();
        return buf;
    }

    /**
     * Creates a direct ByteBuffer from the given InputStream.
     * 
     * @param src Src InputStream.
     * @return A direct ByteBuffer containing the data from the stream.
     * @throws IOException If reading from the stream fails.
     */
    private static ByteBuffer createByteBuffer(InputStream src) throws IOException {
        if (src == null) {
            throw new NullPointerException("src must not be null");
        }

        // Optimize for FileInputStream
        if (src instanceof FileInputStream fis) {
            FileChannel fc = fis.getChannel();
            long size = fc.size();
            if (size > 0 && size <= Integer.MAX_VALUE) {
                ByteBuffer buf = ByteBuffer.allocateDirect((int) size);
                while (buf.hasRemaining()) {
                    if (fc.read(buf) == -1) {
                        break;
                    }
                }
                buf.flip();
                return buf;
            }
        }

        // Fallback for other InputStreams: read directly into a direct ByteBuffer using
        // Channels
        try (ReadableByteChannel channel = Channels.newChannel(src)) {
            int initialSize = Math.max(src.available(), 16384);
            ByteBuffer buf = ByteBuffer.allocateDirect(initialSize);
            while (channel.read(buf) != -1) {
                if (!buf.hasRemaining()) {
                    ByteBuffer newBuf = ByteBuffer.allocateDirect(buf.capacity() * 2);
                    buf.flip();
                    newBuf.put(buf);
                    buf = newBuf;
                }
            }
            buf.flip();
            // Use slice() to return a buffer whose capacity matches the amount of data read
            return buf.slice();
        }
    }

    /**
     * Returns the native pointer to the module.
     * 
     * @return The native module pointer.
     */
    long getModulePtr() {
        return this.modulePtr;
    }

    /**
     * Closes the module and releases native resources.
     */
    @Override
    public void close() throws Exception {
        if (modulePtr != 0) {
            this.closeModule(modulePtr);
        }
        modulePtr = 0;
    }

    /**
     * Returns the WasmtimeEngine associated with this module.
     * 
     * @return The WasmtimeEngine instance.
     */
    public WasmtimeEngine getEngine() {
        return this.engine;
    }

}
