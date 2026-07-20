package io.github.stefanrichterhuber.witparser;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import io.questdb.jar.jni.JarJniLoader;

/**
 * Resolves WIT files into their declared interfaces and functions, via a
 * native binding to the Rust {@code wit-parser} crate -- the same crate
 * {@code wit-bindgen} itself is built on. Used by the WIT-to-Java codegen
 * Maven plugin to generate {@code WasmComponentContext} base classes; this
 * class only performs the parse, it doesn't generate any Java source itself.
 * <br>
 * Supports the full WIT type system {@link WitTypeKind} covers (records,
 * variants, enums, flags, options, results, tuples, lists, resources);
 * unsupported constructs (the WIT 0.3 async surface, {@code stream}/
 * {@code future}, {@code map}, fixed-length lists) throw.
 */
public final class WitParser {

    static {
        JarJniLoader.loadLib(
                WitParser.class,
                "/io/github/stefanrichterhuber/witparser/libs",
                "witparserbinding");
    }

    private WitParser() {
    }

    private static native Object[] parseWitFile(String path);

    private static native Object[] parseWitWorld(String directory, String worldName);

    /**
     * Parses a single {@code .wit} file, or every {@code .wit} file directly
     * within a directory (with an optional {@code deps/} subdirectory of
     * further packages) -- whichever {@code path} is.
     *
     * @param path Path to a {@code .wit} file, or a directory containing one
     *             WIT package (optionally with a {@code deps/} subdirectory
     *             of its dependencies).
     * @return Every interface declared across all resolved packages, in
     *         declaration order.
     */
    public static List<WitInterface> parse(Path path) {
        Object[] raw = parseWitFile(path.toAbsolutePath().toString());
        return Arrays.stream(raw).map(WitInterface.class::cast).toList();
    }

    /**
     * Resolves a named {@code world} within a WIT package directory and
     * returns the flattened, deduplicated set of interfaces it imports
     * (transitively, through any {@code include}s) -- <em>not</em> what it
     * exports, since {@code WasmComponentContext} only models host-provided
     * imports.
     *
     * @param directory Path to the WIT package directory (with an optional
     *                  {@code deps/} subdirectory of its dependencies).
     * @param worldName The bare name of the world to resolve (e.g.
     *                  {@code "command"}). Must match exactly one world
     *                  across every package resolved from {@code directory}.
     * @return The world's transitively imported interfaces.
     */
    public static List<WitInterface> resolveWorld(Path directory, String worldName) {
        Object[] raw = parseWitWorld(directory.toAbsolutePath().toString(), worldName);
        return Arrays.stream(raw).map(WitInterface.class::cast).toList();
    }
}
