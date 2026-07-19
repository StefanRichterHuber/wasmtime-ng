package io.github.stefanrichterhuber.witcodegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
 * Proves the plugin can generate and compile the whole real
 * {@code wasi:cli@0.2.8} "command" world -- multi-package dependency
 * resolution, world/include flattening, and the full WIT type system
 * (resources, records, variants, enums, flags, options, results, tuples)
 * all at once. This is deliberately a compile-only check: full behavioral
 * verification of all ~120 generated functions already exists for the
 * hand-written {@code wasip2} contexts; the bar here is "produces
 * compilable scaffolding for the whole real-world type surface."
 */
class GenerateWitSourcesMojoWorldTest {

    private static final String TARGET_PACKAGE = "io.github.stefanrichterhuber.witcodegen.generated.wasicli";

    @TempDir
    Path tempDir;

    @Test
    void generatesAndCompilesWholeCommandWorld() throws Exception {
        Path worldSourceDirectory = wasiCliWitDirectory();
        Path outputDirectory = tempDir.resolve("generated-sources");

        GenerateWitSourcesMojo mojo = new GenerateWitSourcesMojo();
        mojo.setWitSourceDirectory(tempDir.resolve("no-such-dir").toFile());
        mojo.setWorldSourceDirectory(worldSourceDirectory.toFile());
        mojo.setWorldName("command");
        mojo.setOutputDirectory(outputDirectory.toFile());
        mojo.setTargetPackage(TARGET_PACKAGE);
        MavenProject project = new MavenProject();
        mojo.setProject(project);

        mojo.execute();

        Path packageDir = outputDirectory.resolve(TARGET_PACKAGE.replace('.', '/'));
        List<Path> generatedFiles;
        try (Stream<Path> files = Files.list(packageDir)) {
            generatedFiles = files.filter(p -> p.toString().endsWith(".java")).toList();
        }

        // 27, not 29: "wasi:cli/run" is an export (out of scope), "wasi:clocks/timezone" is
        // gated behind an unstable feature not enabled by default -- see WitParserTest for the
        // full explanation of this count.
        assertEquals(27, generatedFiles.size(), "generated: " + generatedFiles.stream()
                .map(p -> p.getFileName().toString()).collect(Collectors.joining(", ")));
        assertTrue(project.getCompileSourceRoots().contains(outputDirectory.toString()));

        Path classesDir = Files.createDirectories(tempDir.resolve("classes"));
        compile(classesDir, generatedFiles);
    }

    private static Path wasiCliWitDirectory() throws URISyntaxException {
        return Path.of(GenerateWitSourcesMojoWorldTest.class.getResource("/wit/wasi-cli/wit").toURI());
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
