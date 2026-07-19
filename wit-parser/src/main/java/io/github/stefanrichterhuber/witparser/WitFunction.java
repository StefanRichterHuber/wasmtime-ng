package io.github.stefanrichterhuber.witparser;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A single function declared within a {@link WitInterface}.
 *
 * @param name   Function name, as declared in the WIT source.
 * @param params The function's parameters, in declaration order.
 * @param result The function's return type, if it has one.
 */
public record WitFunction(String name, List<WitParam> params, Optional<WitType> result) {

    /**
     * Constructor used by the native {@code wit-parser} binding, which
     * crosses the JNI boundary with plain arrays and WIT type keywords
     * rather than {@link List}/{@link Optional}/{@link WitType}.
     */
    private WitFunction(String name, Object[] params, String resultType) {
        this(name,
                Arrays.stream(params).map(WitParam.class::cast).toList(),
                Optional.ofNullable(resultType).map(WitType::fromWit));
    }
}
