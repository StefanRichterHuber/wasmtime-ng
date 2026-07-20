package io.github.stefanrichterhuber.witcodegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext.ComponentImportFunction;

/**
 * End-to-end verification: runs {@link GenerateWitSourcesMojo} against the
 * same {@code greet} interface {@code GreetComponentContext} (in the
 * {@code wasmtimejavang} module) hand-implements, then actually compiles the
 * generated interface plus a small implementing class and drives it exactly
 * like {@code WasmtimeCustomComponentTest} drives the hand-written version --
 * confirming the generated plumbing produces the same wiring a human would
 * write by hand.
 * <br>
 * Only {@code GreetContextImpl}/{@code GreetContext} are loaded via a
 * dedicated classloader (they're compiled on the fly, into a directory not on
 * the test's own classpath); everything they reference in turn
 * ({@link WasmComponentContext} and friends) resolves back to the test's own
 * classes via normal parent-first delegation, so no reflection is needed to
 * interact with the loaded instance beyond the initial {@code newInstance()}.
 */
class GenerateWitSourcesMojoTest {

    private static final String TARGET_PACKAGE = "io.github.stefanrichterhuber.witcodegen.generated";

    @TempDir
    Path tempDir;

    private Path witSourceDirectory;
    private Path outputDirectory;

    @BeforeEach
    void setUp() throws IOException {
        witSourceDirectory = Files.createDirectories(tempDir.resolve("wit"));
        outputDirectory = tempDir.resolve("generated-sources");
        Files.copy(
                getClass().getResourceAsStream("/wit/greet.wit"),
                witSourceDirectory.resolve("greet.wit"));
    }

    @Test
    void generatesCompilableAbstractContext() throws Exception {
        GenerateWitSourcesMojo mojo = new GenerateWitSourcesMojo();
        mojo.setWitSourceDirectory(witSourceDirectory.toFile());
        mojo.setOutputDirectory(outputDirectory.toFile());
        mojo.setTargetPackage(TARGET_PACKAGE);
        MavenProject project = new MavenProject();
        mojo.setProject(project);

        mojo.execute();

        Path generatedFile = outputDirectory
                .resolve(TARGET_PACKAGE.replace('.', '/'))
                .resolve("GreetContext.java");
        assertTrue(Files.exists(generatedFile), "expected " + generatedFile + " to be generated");
        assertTrue(project.getCompileSourceRoots().contains(outputDirectory.toString()));

        String implSource = """
                package %s;

                public class GreetContextImpl implements GreetContext {
                    @Override
                    public String greetHello(io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance instance, String name) {
                        return "Hello, " + name + "!";
                    }

                    @Override
                    public int greetAdd(io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance instance, int a, int b) {
                        return a + b;
                    }
                }
                """.formatted(TARGET_PACKAGE);

        Path classesDir = Files.createDirectories(tempDir.resolve("classes"));
        Path implSourceFile = writeSource(
                outputDirectory.resolve(TARGET_PACKAGE.replace('.', '/')).resolve("GreetContextImpl.java"),
                implSource);

        compile(classesDir, generatedFile, implSourceFile);

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { classesDir.toUri().toURL() }, getClass().getClassLoader())) {
            Class<?> implClass = Class.forName(TARGET_PACKAGE + ".GreetContextImpl", true, loader);
            WasmComponentContext context = (WasmComponentContext) implClass.getDeclaredConstructor().newInstance();

            List<ComponentImportFunction> importFunctions = context.getImportFunctions();
            assertEquals(2, importFunctions.size());

            assertEquals("Hello, world!", call(importFunctions, "hello", "world")[0]);
            assertEquals(42, call(importFunctions, "add", 40, 2)[0]);
            assertEquals(Set.of("my:custom/greet"), context.getProvidedInterfaces());
        }
    }

    private static Object[] call(List<ComponentImportFunction> importFunctions, String funcName, Object... args) {
        return importFunctions.stream()
                .filter(f -> funcName.equals(f.funcName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no import function named " + funcName))
                .function()
                .call(null, args);
    }

    private static Path writeSource(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private static void compile(Path outputDir, Path... sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "no system Java compiler available");
        String classpath = System.getProperty("java.class.path");
        String[] args = java.util.stream.Stream.concat(
                java.util.stream.Stream.of("-d", outputDir.toString(), "-classpath", classpath),
                java.util.Arrays.stream(sources).map(Path::toString)).toArray(String[]::new);
        int result = compiler.run(null, null, null, args);
        assertEquals(0, result, "compilation of generated sources failed: "
                + java.util.Arrays.stream(sources).map(p -> {
                    try {
                        return Files.readString(p);
                    } catch (IOException e) {
                        return p.toString();
                    }
                }).collect(Collectors.joining("\n---\n")));
    }
}
