package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A NoOp OutputStream doing literally nothing with the bytes written to it
 */
public final class NoOpOutputStream extends OutputStream {

    @Override
    public void write(int b) throws IOException {
        // Do nothing, its NoOp
    }

}
