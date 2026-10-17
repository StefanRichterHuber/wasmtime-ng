package io.github.stefanrichterhuber.witparser;

import java.util.List;

/**
 * A single WIT interface resolved from a {@code .wit} file or world, as
 * returned by {@link WitParser#parse(java.nio.file.Path)} or
 * {@link WitParser#resolveWorld(java.nio.file.Path, String)}.
 *
 * @param name      Fully-qualified, bare (version-independent) interface
 *                  name (e.g. {@code "my:custom/greet"} for
 *                  {@code package my:custom; interface greet { ... }}) --
 *                  the same form {@code WasmComponentContext.getProvidedInterfaces()}
 *                  uses.
 * @param functions The interface's functions, in declaration order.
 * @param resources Bare names of the resource types this interface declares
 *                  (independent of whether any function references them --
 *                  covers resources with zero methods).
 */
public record WitInterface(String name, List<WitFunction> functions, List<String> resources) {
}
