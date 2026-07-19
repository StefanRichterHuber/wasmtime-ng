package io.github.stefanrichterhuber.witparser;

/**
 * A single parameter of a {@link WitFunction}.
 *
 * @param name Parameter name, as declared in the WIT source.
 * @param type The parameter's WIT type.
 */
public record WitParam(String name, WitValueType type) {

    /**
     * Constructor used by the native {@code wit-parser} binding, which only
     * ever deals in raw tag/resource-name strings, not {@link WitValueType}
     * itself.
     */
    private WitParam(String name, String typeTag, String resourceName) {
        this(name, new WitValueType(WitTypeKind.fromTag(typeTag), resourceName));
    }
}
