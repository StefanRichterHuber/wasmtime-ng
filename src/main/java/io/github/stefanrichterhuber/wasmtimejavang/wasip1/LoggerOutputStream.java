package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An OutputStream that redirects its input to a Log4j2 logger.
 * It buffers data until a newline is encountered, then logs the line.
 */
public class LoggerOutputStream extends OutputStream {
    private final Logger logger;
    private final Level level;
    private final Charset charset;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    /**
     * Creates a new LoggerOutputStream.
     * 
     * @param loggerName The name of the logger to use.
     * @param level      The level at which to log.
     */
    public LoggerOutputStream(String loggerName, Level level) {
        this(LogManager.getLogger(loggerName), level, StandardCharsets.UTF_8);
    }

    /**
     * Creates a new LoggerOutputStream.
     * 
     * @param logger The logger to use.
     * @param level  The level at which to log.
     */
    public LoggerOutputStream(Logger logger, Level level) {
        this(logger, level, StandardCharsets.UTF_8);
    }

    /**
     * Creates a new LoggerOutputStream.
     * 
     * @param logger  The logger to use.
     * @param level   The level at which to log.
     * @param charset The charset to use for decoding bytes into strings.
     */
    public LoggerOutputStream(Logger logger, Level level, Charset charset) {
        this.logger = logger;
        this.level = level;
        this.charset = charset;
    }

    @Override
    public synchronized void write(int b) throws IOException {
        if (b == '\n') {
            flushBuffer();
        } else if (b != '\r') {
            buffer.write(b);
        }
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        int start = off;
        for (int i = 0; i < len; i++) {
            int currentPos = off + i;
            if (b[currentPos] == '\n') {
                buffer.write(b, start, currentPos - start);
                flushBuffer();
                start = currentPos + 1;
            } else if (b[currentPos] == '\r') {
                buffer.write(b, start, currentPos - start);
                start = currentPos + 1;
            }
        }
        if (start < off + len) {
            buffer.write(b, start, (off + len) - start);
        }
    }

    private void flushBuffer() {
        if (buffer.size() > 0) {
            String message = buffer.toString(charset);
            logger.log(level, message);
            buffer.reset();
        }
    }

    @Override
    public void flush() throws IOException {
        // Line-buffered output usually doesn't flush on explicit flush()
        // unless we want to force logging partial lines. 
        // For loggers, it is often better to only log complete lines.
    }

    @Override
    public synchronized void close() throws IOException {
        flushBuffer();
        super.close();
    }
}
