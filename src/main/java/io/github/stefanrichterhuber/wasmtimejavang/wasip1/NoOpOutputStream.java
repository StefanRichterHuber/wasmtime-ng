package io.github.stefanrichterhuber.wasmtimejavang.wasip1;

import java.io.IOException;
import java.io.OutputStream;

public final class NoOpOutputStream extends OutputStream {

    @Override
    public void write(int b) throws IOException {
        // Do nothing, its NoOp
    }

}
