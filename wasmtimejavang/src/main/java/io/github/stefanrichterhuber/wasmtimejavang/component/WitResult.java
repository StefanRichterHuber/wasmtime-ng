package io.github.stefanrichterhuber.wasmtimejavang.component;

/**
 * Represents a WIT {@code result&lt;ok, err&gt;} value crossing the
 * Rust/Java boundary. Distinct from {@link java.util.Optional} because a
 * result must distinguish the Ok and Err case even when both carry no
 * (or a {@code null}) payload.
 *
 * @param ok    {@code true} if this is the Ok case, {@code false} if this is
 *              the Err case.
 * @param value The payload of the selected case, or {@code null} if the case
 *              carries no payload.
 */
public record WitResult(boolean ok, Object value) {

    /**
     * Creates a WitResult representing the Ok case.
     *
     * @param value The Ok payload, or {@code null} if the Ok case carries no
     *              payload.
     * @return The created WitResult.
     */
    public static WitResult ok(Object value) {
        return new WitResult(true, value);
    }

    /**
     * Creates a WitResult representing the Err case.
     *
     * @param value The Err payload, or {@code null} if the Err case carries no
     *              payload.
     * @return The created WitResult.
     */
    public static WitResult err(Object value) {
        return new WitResult(false, value);
    }
}
