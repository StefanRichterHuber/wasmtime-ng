package io.github.stefanrichterhuber.witparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class WitParserTest {

    @Test
    void parsesGreetInterface() throws IOException {
        Path witFile = copyResourceToTempFile("/wit/greet.wit");

        List<WitInterface> interfaces = WitParser.parse(witFile);

        assertEquals(1, interfaces.size());
        WitInterface greet = interfaces.get(0);
        assertEquals("my:custom/greet", greet.name());
        assertEquals(2, greet.functions().size());
        assertEquals(List.of(), greet.resources());

        WitFunction hello = greet.functions().get(0);
        assertEquals("hello", hello.name());
        assertEquals(List.of(new WitParam("name", type(WitTypeKind.STRING))), hello.params());
        assertEquals(Optional.of(type(WitTypeKind.STRING)), hello.result());

        WitFunction add = greet.functions().get(1);
        assertEquals("add", add.name());
        assertEquals(
                List.of(new WitParam("a", type(WitTypeKind.U32)), new WitParam("b", type(WitTypeKind.U32))),
                add.params());
        assertEquals(Optional.of(type(WitTypeKind.U32)), add.result());
    }

    @Test
    void throwsOnUnknownFile() {
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> WitParser.parse(Path.of("/no/such/file.wit"))).getMessage()
                .contains("no/such/file.wit"));
    }

    /**
     * Resolves the real {@code wasi:cli@0.2.8} "command" world -- multi-package
     * dependency resolution (deps/clocks,filesystem,io,random,sockets), world
     * include-flattening, and the full WIT type system (resources, records,
     * variants, enums, flags, options, results, tuples) all at once.
     */
    @Test
    void resolvesWasiCliCommandWorld() throws URISyntaxException {
        Path wasiCliWitDir = wasiCliWitDirectory();

        List<WitInterface> interfaces = WitParser.resolveWorld(wasiCliWitDir, "command");

        // 27, not the 29 interfaces that exist across all resolved packages: "wasi:cli/run" is
        // an *export* of `command` (out of scope, see WitParser.resolveWorld's javadoc), and
        // "wasi:clocks/timezone" is gated behind `@unstable(feature = clocks-timezone)` in
        // deps/clocks/world.wit, correctly excluded by wit-parser under default stability
        // settings (matching real wasm-tools/cargo-component behavior).
        assertEquals(27, interfaces.size(), "expected interfaces: " + interfaces.stream()
                .map(WitInterface::name).collect(Collectors.joining(", ")));

        Set<String> names = interfaces.stream().map(WitInterface::name).collect(Collectors.toSet());
        assertTrue(names.contains("wasi:io/streams"));
        assertTrue(names.contains("wasi:filesystem/types"));
        assertTrue(names.contains("wasi:clocks/monotonic-clock"));
        assertTrue(names.contains("wasi:sockets/tcp"));

        WitInterface streams = interfaces.stream().filter(i -> i.name().equals("wasi:io/streams"))
                .findFirst().orElseThrow();
        assertEquals(Set.of("input-stream", "output-stream"), Set.copyOf(streams.resources()));

        WitInterface filesystemTypes = interfaces.stream()
                .filter(i -> i.name().equals("wasi:filesystem/types")).findFirst().orElseThrow();
        assertTrue(filesystemTypes.resources().contains("descriptor"));
        assertTrue(filesystemTypes.resources().contains("directory-entry-stream"));

        // Spot-check a resource method: [method]input-stream.read(self, len) -> result<list<u8>, ...>
        WitFunction read = streams.functions().stream()
                .filter(f -> f.name().equals("[method]input-stream.read")).findFirst().orElseThrow();
        assertEquals(2, read.params().size());
        assertEquals(WitTypeKind.RESOURCE, read.params().get(0).type().kind());
        assertEquals("input-stream", read.params().get(0).type().resourceName());
        assertEquals(Optional.of(type(WitTypeKind.RESULT)), read.result());
    }

    /**
     * Resolves the standalone {@code wasi:sockets@0.2.8} package's own
     * "imports" world -- a package that depends on {@code deps/clocks} and
     * {@code deps/io}, and whose own world is named "imports", exactly like
     * every other individual WASI sub-package's world (see
     * {@link WitParser#resolveWorld}'s javadoc on why the lookup must be
     * scoped to the top-level package rather than matching by bare name
     * across every resolved package).
     */
    @Test
    void resolvesWasiSocketsImportsWorld() throws URISyntaxException {
        Path wasiSocketsWitDir = wasiSocketsWitDirectory();

        List<WitInterface> interfaces = WitParser.resolveWorld(wasiSocketsWitDir, "imports");

        // 7 explicitly imported by wasi:sockets itself, plus wasi:io/{poll,error,streams} and
        // wasi:clocks/monotonic-clock pulled in transitively via `use` (e.g. tcp-socket.accept
        // returns tuple<tcp-socket, input-stream, output-stream>).
        Set<String> names = interfaces.stream().map(WitInterface::name).collect(Collectors.toSet());
        assertEquals(11, interfaces.size(), "expected interfaces: " + String.join(", ", names));
        assertEquals(Set.of(
                "wasi:sockets/network", "wasi:sockets/instance-network", "wasi:sockets/udp",
                "wasi:sockets/udp-create-socket", "wasi:sockets/tcp", "wasi:sockets/tcp-create-socket",
                "wasi:sockets/ip-name-lookup", "wasi:io/poll", "wasi:io/error", "wasi:io/streams",
                "wasi:clocks/monotonic-clock"),
                names);

        WitInterface tcp = interfaces.stream().filter(i -> i.name().equals("wasi:sockets/tcp"))
                .findFirst().orElseThrow();
        assertEquals(List.of("tcp-socket"), tcp.resources());

        // tcp-socket.accept -> result<tuple<own<tcp-socket>, own<input-stream>, own<output-stream>>, error-code>
        WitFunction accept = tcp.functions().stream()
                .filter(f -> f.name().equals("[method]tcp-socket.accept")).findFirst().orElseThrow();
        assertEquals(Optional.of(type(WitTypeKind.RESULT)), accept.result());

        WitInterface network = interfaces.stream().filter(i -> i.name().equals("wasi:sockets/network"))
                .findFirst().orElseThrow();
        assertEquals(List.of("network"), network.resources());
        assertEquals(List.of(), network.functions(), "network's own resource has zero methods");
    }

    private static WitValueType type(WitTypeKind kind) {
        return new WitValueType(kind, null);
    }

    private static Path wasiCliWitDirectory() throws URISyntaxException {
        return Path.of(WitParserTest.class.getResource("/wit/wasi-cli/wit").toURI());
    }

    private static Path wasiSocketsWitDirectory() throws URISyntaxException {
        return Path.of(WitParserTest.class.getResource("/wit/wasi-sockets").toURI());
    }

    private static Path copyResourceToTempFile(String resource) throws IOException {
        Path tempFile = Files.createTempFile("witparsertest", ".wit");
        tempFile.toFile().deleteOnExit();
        try (InputStream is = WitParserTest.class.getResourceAsStream(resource)) {
            Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }
}
