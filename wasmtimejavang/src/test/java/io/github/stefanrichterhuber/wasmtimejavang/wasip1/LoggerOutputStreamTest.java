package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

public class LoggerOutputStreamTest {

    @Test
    public void testLoggerOutputStream() throws IOException {
        Logger logger = mock(Logger.class);
        try (LoggerOutputStream los = new LoggerOutputStream(logger, Level.INFO)) {
            los.write("Hello\nWorld".getBytes(StandardCharsets.UTF_8));
            verify(logger, times(1)).log(eq(Level.INFO), eq("Hello"));

            los.write("\n".getBytes(StandardCharsets.UTF_8));
            verify(logger, times(1)).log(eq(Level.INFO), eq("World"));

            los.write("Partial line".getBytes(StandardCharsets.UTF_8));
            // No new log call yet
        }
        // Closing should flush the partial line
        verify(logger, times(1)).log(eq(Level.INFO), eq("Partial line"));
    }

    @Test
    public void testLoggerOutputStreamWithCarriageReturn() throws IOException {
        Logger logger = mock(Logger.class);
        try (LoggerOutputStream los = new LoggerOutputStream(logger, Level.DEBUG)) {
            los.write("Line 1\r\nLine 2\r\n".getBytes(StandardCharsets.UTF_8));
            verify(logger, times(1)).log(eq(Level.DEBUG), eq("Line 1"));
            verify(logger, times(1)).log(eq(Level.DEBUG), eq("Line 2"));
        }
    }
}
