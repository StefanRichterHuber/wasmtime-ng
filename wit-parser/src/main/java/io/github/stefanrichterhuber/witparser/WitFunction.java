package io.github.stefanrichterhuber.witparser;

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
}
