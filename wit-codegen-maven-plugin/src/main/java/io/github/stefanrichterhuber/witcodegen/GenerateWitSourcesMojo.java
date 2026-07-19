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
 * interface declared in the {@code .wit} files under
 * {@link #witSourceDirectory}, so callers only need to extend the generated
 * class and implement its (typed) abstract methods -- see
 * {@link WitCodeGenerator} for the generated shape.
 */
@Mojo(name = "generate-wit-sources", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresProject = true)
public class GenerateWitSourcesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Directory containing the {@code .wit} files to generate from. Not an
     * error if it doesn't exist -- the goal simply generates nothing.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/wit")
    private File witSourceDirectory;

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
        if (!witSourceDirectory.isDirectory()) {
            getLog().info("No WIT source directory at " + witSourceDirectory + ", nothing to generate");
            return;
        }

        File[] witFiles = witSourceDirectory.listFiles((dir, name) -> name.endsWith(".wit"));
        if (witFiles == null || witFiles.length == 0) {
            getLog().info("No .wit files found in " + witSourceDirectory);
            return;
        }

        Path packageDir = outputDirectory.toPath().resolve(targetPackage.replace('.', '/'));
        try {
            Files.createDirectories(packageDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create " + packageDir, e);
        }

        for (File witFile : Arrays.stream(witFiles).sorted().toList()) {
            List<WitInterface> interfaces;
            try {
                interfaces = WitParser.parse(witFile.toPath());
            } catch (RuntimeException e) {
                throw new MojoExecutionException("Failed to parse " + witFile, e);
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
                        + witFile.getName() + ")");
            }
        }

        project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
    }

    void setWitSourceDirectory(File witSourceDirectory) {
        this.witSourceDirectory = Objects.requireNonNull(witSourceDirectory);
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
