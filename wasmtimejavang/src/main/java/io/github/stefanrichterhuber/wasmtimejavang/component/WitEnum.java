package io.github.stefanrichterhuber.wasmtimejavang.component;

/**
 * Represents a WIT {@code enum} value crossing the Rust/Java boundary.
 * Wrapped explicitly (rather than a plain {@link String}) so it cannot be
 * confused with the WIT {@code string} type on either side of the bridge.
 *
 * @param name The name of the selected enum case.
 */
public record WitEnum(String name) {
}
