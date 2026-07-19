package io.github.stefanrichterhuber.witparser;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import io.questdb.jar.jni.JarJniLoader;

/**
 * Resolves a {@code .wit} file into its declared interfaces and functions,
 * via a native binding to the Rust {@code wit-parser} crate -- the same
 * crate {@code wit-bindgen} itself is built on. Used by the WIT-to-Java
 * codegen Maven plugin to generate {@code WasmComponentContext} base
 * classes; this class only performs the parse, it doesn't generate any Java
 * source itself.
 * <br>
 * Only primitive WIT types are supported so far (see {@link WitType}) --
 * records, variants, enums, flags, lists, options, results, resources, and
 * named type aliases will cause {@link #parse(Path)} to throw.
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

    /**
     * Parses a single {@code .wit} file.
     *
     * @param witFile Path to the {@code .wit} file. Must not depend on any
     *                other package (no {@code use} statements) -- this MVP
     *                resolves a single file in isolation.
     * @return The interfaces declared in the file, in declaration order.
     */
    public static List<WitInterface> parse(Path witFile) {
        Object[] raw = parseWitFile(witFile.toAbsolutePath().toString());
        return Arrays.stream(raw).map(WitInterface.class::cast).toList();
    }
}
