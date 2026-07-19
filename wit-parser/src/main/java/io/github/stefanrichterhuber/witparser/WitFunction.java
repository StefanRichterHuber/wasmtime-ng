package io.github.stefanrichterhuber.witparser;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A single function declared within a {@link WitInterface}.
 * <br>
 * {@code name} is already in the bracketed form {@code wit-parser} itself
 * produces for resource constructors/methods/statics (e.g.
 * {@code "[method]input-stream.read"}) -- the same form the hand-written
 * {@code wasip2} contexts already pass straight through as
 * {@code ComponentImportFunction}'s {@code funcName}. A resource method's
 * implicit receiver is not modeled separately; it's simply the first entry
 * in {@link #params()}, classified like any other {@code WitValueType}.
 *
 * @param name   Function name, as declared in the WIT source.
 * @param params The function's parameters, in declaration order.
 * @param result The function's return type, if it has one.
 */
public record WitFunction(String name, List<WitParam> params, Optional<WitValueType> result) {

    /**
     * Constructor used by the native {@code wit-parser} binding, which
     * crosses the JNI boundary with a plain array and raw tag/resource-name
     * strings rather than {@link List}/{@link Optional}/{@link WitValueType}.
     * {@code resultTag} is {@code null} for a function with no return value.
     */
    private WitFunction(String name, Object[] params, String resultTag, String resultResourceName) {
        this(name,
                Arrays.stream(params).map(WitParam.class::cast).toList(),
                Optional.ofNullable(resultTag)
                        .map(tag -> new WitValueType(WitTypeKind.fromTag(tag), resultResourceName)));
    }
}
