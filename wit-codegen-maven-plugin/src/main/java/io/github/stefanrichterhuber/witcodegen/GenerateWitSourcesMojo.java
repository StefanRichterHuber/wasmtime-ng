package io.github.stefanrichterhuber.witcodegen;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import io.github.stefanrichterhuber.witparser.WitInterface;
import io.github.stefanrichterhuber.witparser.WitParser;

/**
 * Generates one abstract {@code WasmComponentContext} implementation per WIT
 * interface, so callers only need to extend the generated class and
 * implement its (typed) abstract methods -- see {@link WitCodeGenerator} for
 * the generated shape. Two independent, optional sources of interfaces to
 * generate from, either or both of which may be configured:
 * <ul>
 * <li>{@link #witSourceDirectory}: every {@code .wit} file directly within
 * it is parsed and generated from independently (for a standalone custom
 * interface with no world, e.g. {@code greet.wit}).
 * <li>{@link #worldSourceDirectory} + {@link #worldName}: the named world is
 * resolved (with dependency-directory support) and generated from its
 * flattened, transitive set of imported interfaces (for a real WIT package
 * tree, e.g. {@code wasi:cli}'s {@code command} world).
 * </ul>
 */
@Mojo(name = "generate-wit-sources", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresProject = true)
public class GenerateWitSourcesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Directory containing standalone {@code .wit} files to generate from,
     * each parsed independently. Not an error if it doesn't exist.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/wit")
    private File witSourceDirectory;

    /**
     * Directory of a WIT package (optionally with a {@code deps/}
     * subdirectory) to resolve {@link #worldName} from. Both this and
     * {@link #worldName} must be set together to use this generation mode.
     */
    @Parameter
    private File worldSourceDirectory;

    /**
     * The bare name of the world to resolve from {@link #worldSourceDirectory}
     * (e.g. {@code "command"}).
     */
    @Parameter
    private String worldName;

    /**
     * Directory generated {@code .java} sources are written to, and
     * registered as a compile source root.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/wit-components")
    private File outputDirectory;

    /**
     * Java package the generated classes are placed in.
     */
    @Parameter(required = true, property = "witCodegen.targetPackage")
    private String targetPackage;

    @Override
    public void execute() throws MojoExecutionException {
        boolean generatedAnything = false;

        if (witSourceDirectory.isDirectory()) {
            File[] witFiles = witSourceDirectory.listFiles((dir, name) -> name.endsWith(".wit"));
            if (witFiles != null && witFiles.length > 0) {
                for (File witFile : Arrays.stream(witFiles).sorted().toList()) {
                    List<WitInterface> interfaces;
                    try {
                        interfaces = WitParser.parse(witFile.toPath());
                    } catch (RuntimeException e) {
                        throw new MojoExecutionException("Failed to parse " + witFile, e);
                    }
                    generateInterfaces(interfaces, witFile.getName());
                    generatedAnything |= !interfaces.isEmpty();
                }
            } else {
                getLog().info("No .wit files found in " + witSourceDirectory);
            }
        } else {
            getLog().info("No WIT source directory at " + witSourceDirectory + ", skipping single-file generation");
        }

        if (worldSourceDirectory != null && worldName != null) {
            List<WitInterface> interfaces;
            try {
                interfaces = WitParser.resolveWorld(worldSourceDirectory.toPath(), worldName);
            } catch (RuntimeException e) {
                throw new MojoExecutionException(
                        "Failed to resolve world \"" + worldName + "\" in " + worldSourceDirectory, e);
            }
            generateInterfaces(interfaces, "world \"" + worldName + "\"");
            generatedAnything |= !interfaces.isEmpty();
        } else if (worldSourceDirectory != null || worldName != null) {
            throw new MojoExecutionException(
                    "worldSourceDirectory and worldName must both be set to generate from a WIT world");
        }

        if (generatedAnything) {
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        }
    }

    private void generateInterfaces(List<WitInterface> interfaces, String sourceDescription)
            throws MojoExecutionException {
        Path packageDir = outputDirectory.toPath().resolve(targetPackage.replace('.', '/'));
        try {
            Files.createDirectories(packageDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create " + packageDir, e);
        }

        for (WitInterface iface : interfaces) {
            String className = WitCodeGenerator.className(iface.name());
            String source = WitCodeGenerator.generate(targetPackage, iface);
            Path outFile = packageDir.resolve(className + ".java");
            try {
                Files.writeString(outFile, source, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to write " + outFile, e);
            }
            getLog().info("Generated " + outFile + " from interface \"" + iface.name() + "\" ("
                    + sourceDescription + ")");
        }
    }

    void setWitSourceDirectory(File witSourceDirectory) {
        this.witSourceDirectory = Objects.requireNonNull(witSourceDirectory);
    }

    void setWorldSourceDirectory(File worldSourceDirectory) {
        this.worldSourceDirectory = worldSourceDirectory;
    }

    void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = Objects.requireNonNull(outputDirectory);
    }

    void setTargetPackage(String targetPackage) {
        this.targetPackage = Objects.requireNonNull(targetPackage);
    }

    void setProject(MavenProject project) {
        this.project = Objects.requireNonNull(project);
    }
}
