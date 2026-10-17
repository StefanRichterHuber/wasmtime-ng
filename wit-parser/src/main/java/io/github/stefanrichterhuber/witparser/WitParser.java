package io.github.stefanrichterhuber.witparser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.stefanrichterhuber.wasmtimejavang.WasiPI1Context;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeEngine;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeInstance;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeLinker;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeModule;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeStore;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.ProcExitException;

/**
 * Resolves WIT files into their declared interfaces and functions, via the
 * Rust {@code wit-parser} crate -- the same crate {@code wit-bindgen} itself
 * is built on -- compiled to a platform independent {@code wasm32-wasip1}
 * module and run on wasmtime-java-ng. Used by the WIT-to-Java codegen Maven
 * plugin to generate {@code WasmComponentContext} base classes; this class
 * only performs the parse, it doesn't generate any Java source itself.
 * <br>
 * Supports the full WIT type system {@link WitTypeKind} covers (records,
 * variants, enums, flags, options, results, tuples, lists, resources);
 * unsupported constructs (the WIT 0.3 async surface, {@code stream}/
 * {@code future}, {@code map}, fixed-length lists) throw.
 */
public final class WitParser {

    /**
     * Classpath location of the wit-parser WASI module
     */
    private static final String WASM_RESOURCE = "wit-parser-binding.wasm";

    /**
     * Path the WIT directory is preopened as within the WASI module
     */
    private static final String GUEST_DIRECTORY = "/wit";

    /**
     * Lazily compiles the module once per class loader -- compiling is by far
     * the most expensive step, instantiating it per call is cheap.
     */
    private static final class Module {
        private static final WasmtimeEngine ENGINE = new WasmtimeEngine();
        private static final WasmtimeModule MODULE = load();

        private static WasmtimeModule load() {
            try (InputStream is = WitParser.class.getResourceAsStream(WASM_RESOURCE)) {
                if (is == null) {
                    throw new IllegalStateException("WASM module " + WASM_RESOURCE + " not found on classpath");
                }
                return new WasmtimeModule(ENGINE, is);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to load WASM module " + WASM_RESOURCE, e);
            }
        }
    }

    private WitParser() {
    }

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
        Path absolute = requireExisting(path);
        if (Files.isDirectory(absolute)) {
            return run(absolute, absolute, List.of("file", GUEST_DIRECTORY));
        }
        return run(absolute, absolute.getParent(),
                List.of("file", GUEST_DIRECTORY + "/" + absolute.getFileName()));
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
        Path absolute = requireExisting(directory);
        return run(absolute, absolute, List.of("world", GUEST_DIRECTORY, worldName));
    }

    private static Path requireExisting(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (!Files.exists(absolute)) {
            throw new IllegalArgumentException("WIT path does not exist: " + displayPath(absolute));
        }
        return absolute;
    }

    private static String displayPath(Path path) {
        return path.toString().replace(File.separatorChar, '/');
    }

    /**
     * Runs the wit-parser WASI command with {@code hostDirectory} preopened as
     * {@link #GUEST_DIRECTORY} and decodes the JSON it writes to stdout.
     *
     * @param path          The host path being parsed, only used for error messages.
     * @param hostDirectory The host directory to make visible to the module.
     * @param args          The command arguments (without the program name).
     * @return The decoded interfaces.
     */
    private static List<WitInterface> run(Path path, Path hostDirectory, List<String> args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        List<String> argv = new ArrayList<>();
        argv.add("wit-parser");
        argv.addAll(args);

        try (WasmtimeStore store = new WasmtimeStore(Module.ENGINE);
                WasmtimeLinker linker = new WasmtimeLinker(Module.ENGINE, store)) {
            linker.linkContext(new WasiPI1Context()
                    .withDirectory(hostDirectory, GUEST_DIRECTORY)
                    .withArguments(argv)
                    .withStdOut(stdout)
                    .withStdErr(stderr));

            try (WasmtimeInstance instance = new WasmtimeInstance(store, Module.MODULE, linker)) {
                instance.start();
            } catch (ProcExitException e) {
                if (e.getCode() != 0) {
                    throw new RuntimeException("Failed to parse WIT at " + displayPath(path) + ": "
                            + stderr.toString(StandardCharsets.UTF_8).strip(), e);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // Only thrown by close() of the runtime objects
            throw new IllegalStateException("Failed to release WASM runtime resources", e);
        }

        return decodeInterfaces(stdout.toString(StandardCharsets.UTF_8));
    }

    private static List<WitInterface> decodeInterfaces(String json) {
        List<WitInterface> interfaces = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject iface = element.getAsJsonObject();

            List<WitFunction> functions = new ArrayList<>();
            for (JsonElement f : iface.getAsJsonArray("functions")) {
                functions.add(decodeFunction(f.getAsJsonObject()));
            }
            List<String> resources = new ArrayList<>();
            for (JsonElement r : iface.getAsJsonArray("resources")) {
                resources.add(r.getAsString());
            }

            interfaces.add(new WitInterface(iface.get("name").getAsString(), List.copyOf(functions),
                    List.copyOf(resources)));
        }
        return List.copyOf(interfaces);
    }

    private static WitFunction decodeFunction(JsonObject function) {
        List<WitParam> params = new ArrayList<>();
        for (JsonElement p : function.getAsJsonArray("params")) {
            JsonObject param = p.getAsJsonObject();
            params.add(new WitParam(param.get("name").getAsString(), decodeType(param)));
        }
        JsonElement result = function.get("result");
        return new WitFunction(function.get("name").getAsString(), List.copyOf(params),
                result == null || result.isJsonNull()
                        ? Optional.empty()
                        : Optional.of(decodeType(result.getAsJsonObject())));
    }

    private static WitValueType decodeType(JsonObject type) {
        JsonElement resourceName = type.get("resource_name");
        return new WitValueType(WitTypeKind.fromTag(type.get("type_tag").getAsString()),
                resourceName == null || resourceName.isJsonNull() ? null : resourceName.getAsString());
    }
}
