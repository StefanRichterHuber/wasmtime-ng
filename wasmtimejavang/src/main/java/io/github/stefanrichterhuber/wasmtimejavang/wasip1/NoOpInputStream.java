package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.io.IOException;
import java.io.InputStream;

/**
 * A no-op InputStream which has no bytes available
 */
public final class NoOpInputStream extends InputStream {

    @Override
    public int read() throws IOException {
        return -1;
    }

    @Override
    public int available() throws IOException {
        return 0;
    }
}
