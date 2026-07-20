package io.github.stefanrichterhuber.wasmtimejavang.component;

/**
 * Represents a WIT {@code variant} value crossing the Rust/Java boundary.
 * A variant carries the name of the selected case and, optionally, a payload
 * value for that case.
 *
 * @param caseName The name of the selected variant case.
 * @param value    The payload of the selected case, or {@code null} if the
 *                  case has no payload.
 */
public record WitVariant(String caseName, Object value) {
}
