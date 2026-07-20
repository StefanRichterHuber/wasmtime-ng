package io.github.stefanrichterhuber.witcodegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the plugin can generate and compile whole real WASI worlds --
 * multi-package dependency resolution, world/include flattening, and the
 * full WIT type system (resources, records, variants, enums, flags,
 * options, results, tuples) all at once. Covers two cases: {@code
 * wasi:cli@0.2.8}'s "command" world (a package whose imports are entirely
 * pulled in through nested {@code include}s of its dependencies' own
 * worlds) and the standalone {@code wasi:sockets@0.2.8} package's own
 * "imports" world (which shares its world *name* -- "imports" -- with
 * every other individual WASI sub-package, exercising the package-scoped
 * world lookup {@link io.github.stefanrichterhuber.witparser.WitParser#resolveWorld}
 * needs to disambiguate). This is deliberately a compile-only check: full
 * behavioral verification of every generated function already exists for
 * the hand-written {@code wasip2} contexts; the bar here is "produces
 * compilable scaffolding for the whole real-world type surface."
 */
class GenerateWitSourcesMojoWorldTest {

    private static final String CLI_TARGET_PACKAGE = "io.github.stefanrichterhuber.witcodegen.generated.wasicli";
    private static final String SOCKETS_TARGET_PACKAGE =
            "io.github.stefanrichterhuber.witcodegen.generated.wasisockets";

    @TempDir
    Path tempDir;

    @Test
    void generatesAndCompilesWholeCommandWorld() throws Exception {
        Path outputDirectory = tempDir.resolve("generated-sources");

        List<Path> generatedFiles = generate(wasiCliWitDirectory(), "command", outputDirectory,
                CLI_TARGET_PACKAGE);

        // 27, not 29: "wasi:cli/run" is an export (out of scope), "wasi:clocks/timezone" is
        // gated behind an unstable feature not enabled by default -- see WitParserTest for the
        // full explanation of this count.
        assertEquals(27, generatedFiles.size(), "generated: " + generatedFiles.stream()
                .map(p -> p.getFileName().toString()).collect(Collectors.joining(", ")));

        compile(Files.createDirectories(tempDir.resolve("classes")), generatedFiles);
    }

    @Test
    void generatesAndCompilesWholeSocketsImportsWorld() throws Exception {
        Path outputDirectory = tempDir.resolve("generated-sources");

        List<Path> generatedFiles = generate(wasiSocketsWitDirectory(), "imports", outputDirectory,
                SOCKETS_TARGET_PACKAGE);

        // 7 explicitly imported by wasi:sockets itself, plus wasi:io/{poll,error,streams} and
        // wasi:clocks/monotonic-clock pulled in transitively via `use` -- see WitParserTest's
        // resolvesWasiSocketsImportsWorld for the full explanation of this count.
        assertEquals(11, generatedFiles.size(), "generated: " + generatedFiles.stream()
                .map(p -> p.getFileName().toString()).collect(Collectors.joining(", ")));

        compile(Files.createDirectories(tempDir.resolve("classes")), generatedFiles);
    }

    private List<Path> generate(Path worldSourceDirectory, String worldName, Path outputDirectory,
            String targetPackage) throws Exception {
        GenerateWitSourcesMojo mojo = new GenerateWitSourcesMojo();
        mojo.setWitSourceDirectory(tempDir.resolve("no-such-dir").toFile());
        mojo.setWorldSourceDirectory(worldSourceDirectory.toFile());
        mojo.setWorldName(worldName);
        mojo.setOutputDirectory(outputDirectory.toFile());
        mojo.setTargetPackage(targetPackage);
        MavenProject project = new MavenProject();
        mojo.setProject(project);

        mojo.execute();

        assertTrue(project.getCompileSourceRoots().contains(outputDirectory.toString()));

        Path packageDir = outputDirectory.resolve(targetPackage.replace('.', '/'));
        try (Stream<Path> files = Files.list(packageDir)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static Path wasiCliWitDirectory() throws URISyntaxException {
        return Path.of(GenerateWitSourcesMojoWorldTest.class.getResource("/wit/wasi-cli/wit").toURI());
    }

    private static Path wasiSocketsWitDirectory() throws URISyntaxException {
        return Path.of(GenerateWitSourcesMojoWorldTest.class.getResource("/wit/wasi-sockets").toURI());
    }

    private static void compile(Path outputDir, List<Path> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "no system Java compiler available");
        String classpath = System.getProperty("java.class.path");
        String[] args = Stream.concat(
                Stream.of("-d", outputDir.toString(), "-classpath", classpath),
                sources.stream().map(Path::toString)).toArray(String[]::new);
        int result = compiler.run(null, null, null, args);
        assertEquals(0, result, "compilation of generated sources failed (see stderr above for javac diagnostics)");
    }
}
