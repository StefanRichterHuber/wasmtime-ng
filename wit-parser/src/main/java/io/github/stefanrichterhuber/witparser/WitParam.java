package io.github.stefanrichterhuber.witparser;

/**
 * A single parameter of a {@link WitFunction}.
 *
 * @param name Parameter name, as declared in the WIT source.
 * @param type The parameter's WIT type.
 */
public record WitParam(String name, WitType type) {

    /**
     * Constructor used by the native {@code wit-parser} binding, which only
     * ever deals in WIT type keywords (e.g. {@code "u32"}), not the Java
     * {@link WitType} enum itself.
     */
    private WitParam(String name, String type) {
        this(name, WitType.fromWit(type));
    }
}
