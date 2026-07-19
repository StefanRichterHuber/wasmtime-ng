package io.github.stefanrichterhuber.witparser;

import java.util.Arrays;
import java.util.List;

/**
 * A single WIT interface resolved from a {@code .wit} file, as returned by
 * {@link WitParser#parse(java.nio.file.Path)}.
 *
 * @param name      Interface name, as declared in the WIT source (e.g.
 *                  {@code "greet"} for {@code interface greet { ... }}).
 * @param functions The interface's functions, in declaration order.
 */
public record WitInterface(String name, List<WitFunction> functions) {

    /**
     * Constructor used by the native {@code wit-parser} binding, which
     * crosses the JNI boundary with a plain array rather than a {@link List}.
     */
    private WitInterface(String name, Object[] functions) {
        this(name, Arrays.stream(functions).map(WitFunction.class::cast).toList());
    }
}
