package io.github.stefanrichterhuber.witparser;

/**
 * The WIT type of a {@link WitParam} or a {@link WitFunction}'s result,
 * classified down to a {@link WitTypeKind}.
 *
 * @param kind         The classified shape.
 * @param resourceName The bare resource name (e.g. {@code "descriptor"}),
 *                      populated only when {@code kind == WitTypeKind.RESOURCE}
 *                      -- purely informational, the Java type is
 *                      {@code WitResource} either way.
 */
public record WitValueType(WitTypeKind kind, String resourceName) {
}
