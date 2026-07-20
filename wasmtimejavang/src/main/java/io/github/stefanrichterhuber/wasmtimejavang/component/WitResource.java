package io.github.stefanrichterhuber.wasmtimejavang.component;

/**
 * Represents a WIT resource handle crossing the Rust/Java boundary.
 * <br>
 * The {@code rep} is an opaque integer chosen by whichever Java code created
 * the resource (typically a key into a per-context table, the same way WASI
 * Preview 1 uses plain {@code int} file descriptors).
 * <br>
 * {@code resourceName} identifies the resource's WIT name (e.g.
 * {@code "output-stream"}) and is required when Java code constructs a
 * WitResource to return from an import function (the Rust side needs it to
 * build a value of the matching resource type). It is not populated when a
 * WitResource is handed to Java as an incoming function argument -- the
 * receiving Java code already knows the resource kind from which import
 * function was invoked, the same way WASI Preview 1 file descriptors carry no
 * type tag either.
 *
 * @param resourceName The WIT name of the resource kind, required only when
 *                      constructing a WitResource to return to the guest.
 * @param rep           The opaque resource representation (table key).
 * @param owned         {@code true} if this handle owns the resource (the
 *                      guest will eventually drop it, triggering the
 *                      destructor), {@code false} if this is a borrow.
 */
public record WitResource(String resourceName, int rep, boolean owned) {

    /**
     * Creates an owned WitResource to return to the guest.
     *
     * @param resourceName The WIT name of the resource kind (e.g.
     *                     {@code "output-stream"}).
     * @param rep          The opaque resource representation (table key).
     * @return The created WitResource.
     */
    public static WitResource own(String resourceName, int rep) {
        return new WitResource(resourceName, rep, true);
    }

    /**
     * Creates a borrowed WitResource to return to the guest.
     *
     * @param resourceName The WIT name of the resource kind (e.g.
     *                     {@code "output-stream"}).
     * @param rep          The opaque resource representation (table key).
     * @return The created WitResource.
     */
    public static WitResource borrow(String resourceName, int rep) {
        return new WitResource(resourceName, rep, false);
    }
}
