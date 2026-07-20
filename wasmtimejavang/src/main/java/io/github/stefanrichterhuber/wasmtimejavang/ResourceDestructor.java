package io.github.stefanrichterhuber.wasmtimejavang;

/**
 * Callback invoked when a guest drops an owned component resource instance.
 * Top-level (rather than nested in {@link WasmComponentContext}) so it can be
 * bound directly as a JNI callback target.
 */
@FunctionalInterface
public interface ResourceDestructor {
    /**
     * Releases whatever the given resource representation refers to.
     *
     * @param rep The opaque resource representation (table key) originally
     *            handed out when the resource was created.
     */
    void drop(int rep);
}
