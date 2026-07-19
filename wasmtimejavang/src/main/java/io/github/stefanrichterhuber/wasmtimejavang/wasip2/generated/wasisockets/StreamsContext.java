package io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasisockets;

import java.util.List;
import java.util.Set;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Generated from WIT interface "wasi:io/streams" by wit-codegen-maven-plugin.
 * Do not edit directly -- implement this interface (alongside any others,
 * on the same class if they belong to the same bundle) instead.
 */
public interface StreamsContext extends WasmComponentContext {
    String INTERFACE = "wasi:io/streams";

    @Override
    default String name() {
        return "streams";
    }

    private String versioned() {
        return INTERFACE + "@" + getVersion();
    }

    @Override
    default List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(versioned(), "[method]input-stream.read", this::inputStreamReadImpl),
                new ComponentImportFunction(versioned(), "[method]input-stream.blocking-read", this::inputStreamBlockingReadImpl),
                new ComponentImportFunction(versioned(), "[method]input-stream.skip", this::inputStreamSkipImpl),
                new ComponentImportFunction(versioned(), "[method]input-stream.blocking-skip", this::inputStreamBlockingSkipImpl),
                new ComponentImportFunction(versioned(), "[method]input-stream.subscribe", this::inputStreamSubscribeImpl),
                new ComponentImportFunction(versioned(), "[method]output-stream.check-write", this::outputStreamCheckWriteImpl),
                new ComponentImportFunction(versioned(), "[method]output-stream.write", this::outputStreamWriteImpl),
                new ComponentImportFunction(versioned(), "[method]output-stream.blocking-write-and-flush", this::outputStreamBlockingWriteAndFlushImpl),
                new ComponentImportFunction(versioned(), "[method]output-stream.flush", this::outputStreamFlushImpl),
                new ComponentImportFunction(versioned(), "[method]output-stream.blocking-flush", this::outputStreamBlockingFlushImpl),
                new ComponentImportFunction(versioned(), "[method]output-stream.subscribe", this::outputStreamSubscribeImpl),
                new ComponentImportFunction(versioned(), "[method]output-stream.write-zeroes", this::outputStreamWriteZeroesImpl),
                new ComponentImportFunction(versioned(), "[method]output-stream.blocking-write-zeroes-and-flush", this::outputStreamBlockingWriteZeroesAndFlushImpl),
                new ComponentImportFunction(versioned(), "[method]output-stream.splice", this::outputStreamSpliceImpl),
                new ComponentImportFunction(versioned(), "[method]output-stream.blocking-splice", this::outputStreamBlockingSpliceImpl)
            );
    }

    @Override
    default List<ComponentImportResource> getImportResources() {
        return List.of(
                new ComponentImportResource(versioned(), "input-stream", this::dropInputStream),
                new ComponentImportResource(versioned(), "output-stream", this::dropOutputStream),
                new ComponentImportResource(versioned(), "error", this::dropError),
                new ComponentImportResource(versioned(), "pollable", this::dropPollable)
            );
    }

    @Override
    default Set<String> getProvidedInterfaces() {
        return Set.of(INTERFACE);
    }

    WitResult inputStreamRead(WasmtimeComponentInstance instance, WitResource self, long len);

    WitResult inputStreamBlockingRead(WasmtimeComponentInstance instance, WitResource self, long len);

    WitResult inputStreamSkip(WasmtimeComponentInstance instance, WitResource self, long len);

    WitResult inputStreamBlockingSkip(WasmtimeComponentInstance instance, WitResource self, long len);

    WitResource inputStreamSubscribe(WasmtimeComponentInstance instance, WitResource self);

    WitResult outputStreamCheckWrite(WasmtimeComponentInstance instance, WitResource self);

    WitResult outputStreamWrite(WasmtimeComponentInstance instance, WitResource self, byte[] contents);

    WitResult outputStreamBlockingWriteAndFlush(WasmtimeComponentInstance instance, WitResource self, byte[] contents);

    WitResult outputStreamFlush(WasmtimeComponentInstance instance, WitResource self);

    WitResult outputStreamBlockingFlush(WasmtimeComponentInstance instance, WitResource self);

    WitResource outputStreamSubscribe(WasmtimeComponentInstance instance, WitResource self);

    WitResult outputStreamWriteZeroes(WasmtimeComponentInstance instance, WitResource self, long len);

    WitResult outputStreamBlockingWriteZeroesAndFlush(WasmtimeComponentInstance instance, WitResource self, long len);

    WitResult outputStreamSplice(WasmtimeComponentInstance instance, WitResource self, WitResource src, long len);

    WitResult outputStreamBlockingSplice(WasmtimeComponentInstance instance, WitResource self, WitResource src, long len);

    void dropInputStream(int rep);

    void dropOutputStream(int rep);

    void dropError(int rep);

    void dropPollable(int rep);

    private Object[] inputStreamReadImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long len = (Long) args[1];
        return new Object[] { inputStreamRead(instance, self, len) };
    }

    private Object[] inputStreamBlockingReadImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long len = (Long) args[1];
        return new Object[] { inputStreamBlockingRead(instance, self, len) };
    }

    private Object[] inputStreamSkipImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long len = (Long) args[1];
        return new Object[] { inputStreamSkip(instance, self, len) };
    }

    private Object[] inputStreamBlockingSkipImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long len = (Long) args[1];
        return new Object[] { inputStreamBlockingSkip(instance, self, len) };
    }

    private Object[] inputStreamSubscribeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { inputStreamSubscribe(instance, self) };
    }

    private Object[] outputStreamCheckWriteImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outputStreamCheckWrite(instance, self) };
    }

    private Object[] outputStreamWriteImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        byte[] contents = (byte[]) args[1];
        return new Object[] { outputStreamWrite(instance, self, contents) };
    }

    private Object[] outputStreamBlockingWriteAndFlushImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        byte[] contents = (byte[]) args[1];
        return new Object[] { outputStreamBlockingWriteAndFlush(instance, self, contents) };
    }

    private Object[] outputStreamFlushImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outputStreamFlush(instance, self) };
    }

    private Object[] outputStreamBlockingFlushImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outputStreamBlockingFlush(instance, self) };
    }

    private Object[] outputStreamSubscribeImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        return new Object[] { outputStreamSubscribe(instance, self) };
    }

    private Object[] outputStreamWriteZeroesImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long len = (Long) args[1];
        return new Object[] { outputStreamWriteZeroes(instance, self, len) };
    }

    private Object[] outputStreamBlockingWriteZeroesAndFlushImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        long len = (Long) args[1];
        return new Object[] { outputStreamBlockingWriteZeroesAndFlush(instance, self, len) };
    }

    private Object[] outputStreamSpliceImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        WitResource src = (WitResource) args[1];
        long len = (Long) args[2];
        return new Object[] { outputStreamSplice(instance, self, src, len) };
    }

    private Object[] outputStreamBlockingSpliceImpl(WasmtimeComponentInstance instance, Object... args) {
        WitResource self = (WitResource) args[0];
        WitResource src = (WitResource) args[1];
        long len = (Long) args[2];
        return new Object[] { outputStreamBlockingSplice(instance, self, src, len) };
    }

}
