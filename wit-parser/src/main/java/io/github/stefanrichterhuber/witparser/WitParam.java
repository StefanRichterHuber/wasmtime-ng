package io.github.stefanrichterhuber.witparser;

/**
 * A single parameter of a {@link WitFunction}.
 *
 * @param name Parameter name, as declared in the WIT source.
 * @param type The parameter's WIT type.
 */
public record WitParam(String name, WitValueType type) {
}
