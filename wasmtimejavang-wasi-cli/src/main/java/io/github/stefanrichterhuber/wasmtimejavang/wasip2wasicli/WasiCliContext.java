package io.github.stefanrichterhuber.wasmtimejavang.wasip2wasicli;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup;
import io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.NoOpInputStream;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.NoOpOutputStream;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.ProcExitException;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli.EnvironmentContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli.ExitContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli.StderrContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli.StdinContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli.StdoutContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli.TerminalInputContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli.TerminalOutputContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli.TerminalStderrContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli.TerminalStdinContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasicli.TerminalStdoutContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiio.WasiIoContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiio.WasiIoResources;

/**
 * Implementation of the {@code wasi:cli/*} interfaces (WASI Preview 2,
 * 0.2.6): environment, exit, stdio, and terminal-* stubs -- the
 * {@code "wasi-cli"} component context.
 * <br>
 * Implements all ten generated interfaces at once.
 * <br>
 * Depends on {@code "wasi-io"} ({@link WasiIoResources}) to actually register
 * the {@code input-stream}/{@code output-stream} resources
 * {@code get-stdin}/{@code get-stdout}/{@code get-stderr} hand out: those are
 * the same resource kinds (and, for a well-formed component, the same
 * underlying type) {@code wasi:io/streams} operates on.
 * <br>
 * {@code get-stdin} registers the configured stream with {@code "wasi-io"}
 * the same way {@code get-stdout}/{@code get-stderr} do; see
 * {@link WasiIoContext}'s class javadoc for the (blocking-only) read support
 * a guest gets from the resulting resource.
 */
public class WasiCliContext implements EnvironmentContext, ExitContext, StdinContext, StdoutContext, StderrContext,
        TerminalInputContext, TerminalOutputContext, TerminalStdinContext, TerminalStdoutContext,
        TerminalStderrContext {
    private static final Logger LOGGER = LogManager.getLogger();

    /** The stable name other contexts reference via {@code getDependencies()}. */
    public static final String NAME = "wasi-cli";

    private final Map<String, String> env = new HashMap<>();
    private final List<String> args = new ArrayList<>();
    private InputStream stdin = new NoOpInputStream();
    private OutputStream stdout = new NoOpOutputStream();
    private OutputStream stderr = new NoOpOutputStream();
    private SemanticVersion version = DEFAULT_VERSION;

    private WasiIoResources io;

    /**
     * Sets the environment variables exposed via {@code wasi:cli/environment}.
     *
     * @param env A map of environment variables.
     * @return This context.
     */
    public WasiCliContext withEnvs(Map<String, String> env) {
        this.env.putAll(env);
        return this;
    }

    /**
     * Sets the command-line arguments exposed via
     * {@code wasi:cli/environment#get-arguments}.
     *
     * @param args The arguments.
     * @return This context.
     */
    public WasiCliContext withArguments(List<String> args) {
        this.args.addAll(args);
        return this;
    }

    /**
     * Sets the standard output stream.
     *
     * @param stdout The stdout stream.
     * @return This context.
     */
    public WasiCliContext withStdOut(OutputStream stdout) {
        this.stdout = stdout;
        return this;
    }

    /**
     * Sets the standard error stream.
     *
     * @param stderr The stderr stream.
     * @return This context.
     */
    public WasiCliContext withStdErr(OutputStream stderr) {
        this.stderr = stderr;
        return this;
    }

    /**
     * Sets the standard input stream.
     *
     * @param stdin The stdin stream.
     * @return This context.
     */
    public WasiCliContext withStdIn(InputStream stdin) {
        this.stdin = stdin;
        return this;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> getProvidedInterfaces() {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(EnvironmentContext.super.getProvidedInterfaces());
        result.addAll(ExitContext.super.getProvidedInterfaces());
        result.addAll(StdinContext.super.getProvidedInterfaces());
        result.addAll(StdoutContext.super.getProvidedInterfaces());
        result.addAll(StderrContext.super.getProvidedInterfaces());
        result.addAll(TerminalInputContext.super.getProvidedInterfaces());
        result.addAll(TerminalOutputContext.super.getProvidedInterfaces());
        result.addAll(TerminalStdinContext.super.getProvidedInterfaces());
        result.addAll(TerminalStdoutContext.super.getProvidedInterfaces());
        result.addAll(TerminalStderrContext.super.getProvidedInterfaces());
        return result;
    }

    @Override
    public List<String> getDependencies() {
        return List.of(WasiIoContext.NAME);
    }

    @Override
    public void onDependenciesResolved(ComponentContextLookup lookup) {
        this.io = (WasiIoResources) lookup.resolve(WasiIoContext.NAME, getVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "\"" + NAME + "\" requires a \"" + WasiIoContext.NAME + "\" dependency implementing "
                                + WasiIoResources.class.getSimpleName()));
    }

    @Override
    public List<ComponentImportFunction> getImportFunctions() {
        List<ComponentImportFunction> result = new ArrayList<>();
        result.addAll(EnvironmentContext.super.getImportFunctions());
        result.addAll(ExitContext.super.getImportFunctions());
        result.addAll(StdinContext.super.getImportFunctions());
        result.addAll(StdoutContext.super.getImportFunctions());
        result.addAll(StderrContext.super.getImportFunctions());
        result.addAll(TerminalInputContext.super.getImportFunctions());
        result.addAll(TerminalOutputContext.super.getImportFunctions());
        result.addAll(TerminalStdinContext.super.getImportFunctions());
        result.addAll(TerminalStdoutContext.super.getImportFunctions());
        result.addAll(TerminalStderrContext.super.getImportFunctions());
        return result;
    }

    @Override
    public List<ComponentImportResource> getImportResources() {
        List<ComponentImportResource> result = new ArrayList<>();
        result.addAll(EnvironmentContext.super.getImportResources());
        result.addAll(ExitContext.super.getImportResources());
        result.addAll(StdinContext.super.getImportResources());
        result.addAll(StdoutContext.super.getImportResources());
        result.addAll(StderrContext.super.getImportResources());
        result.addAll(TerminalInputContext.super.getImportResources());
        result.addAll(TerminalOutputContext.super.getImportResources());
        result.addAll(TerminalStdinContext.super.getImportResources());
        result.addAll(TerminalStdoutContext.super.getImportResources());
        result.addAll(TerminalStderrContext.super.getImportResources());
        return result;
    }

    @Override
    public void dropInputStream(int rep) {
        io.dropInputStream(rep);
    }

    @Override
    public void dropOutputStream(int rep) {
        io.dropOutputStream(rep);
    }

    @Override
    public void dropTerminalInput(int rep) {
        // terminal-input is never actually constructed by this implementation
        // (get-terminal-* always answers "not a tty"), so there is nothing to
        // release.
    }

    @Override
    public void dropTerminalOutput(int rep) {
        // Same as dropTerminalInput: never actually constructed.
    }

    @Override
    public List<Object> environmentGetEnvironment(WasmtimeComponentInstance instance) {
        List<Object> entries = new ArrayList<>();
        for (Map.Entry<String, String> e : env.entrySet()) {
            entries.add(new Object[] { e.getKey(), e.getValue() });
        }
        return entries;
    }

    @Override
    public List<Object> environmentGetArguments(WasmtimeComponentInstance instance) {
        return List.copyOf(this.args);
    }

    @Override
    public Optional<Object> environmentInitialCwd(WasmtimeComponentInstance instance) {
        // No defined initial working directory in this implementation.
        return Optional.empty();
    }

    @Override
    public void exitExit(WasmtimeComponentInstance instance, WitResult status) {
        LOGGER.debug("Wasm component called wasi:cli/exit with ok={}", status.ok());
        throw new ProcExitException(status.ok() ? 0 : 1);
    }

    @Override
    public WitResource stdinGetStdin(WasmtimeComponentInstance instance) {
        int rep = io.registerInputStream(stdin);
        return WitResource.own("input-stream", rep);
    }

    @Override
    public WitResource stdoutGetStdout(WasmtimeComponentInstance instance) {
        int rep = io.registerOutputStream(stdout);
        return WitResource.own("output-stream", rep);
    }

    @Override
    public WitResource stderrGetStderr(WasmtimeComponentInstance instance) {
        int rep = io.registerOutputStream(stderr);
        return WitResource.own("output-stream", rep);
    }

    @Override
    public Optional<Object> terminalStdinGetTerminalStdin(WasmtimeComponentInstance instance) {
        return Optional.empty();
    }

    @Override
    public Optional<Object> terminalStdoutGetTerminalStdout(WasmtimeComponentInstance instance) {
        return Optional.empty();
    }

    @Override
    public Optional<Object> terminalStderrGetTerminalStderr(WasmtimeComponentInstance instance) {
        return Optional.empty();
    }

    @Override
    public WasiCliContext withVersion(SemanticVersion version) {
        if (!supportsVersion(version)) {
            throw new IllegalArgumentException("Unsupported version: " + version);
        }
        this.version = version;
        return this;
    }

    @Override
    public SemanticVersion getVersion() {
        return this.version;
    }

    @Override
    public SemanticVersion getMiniumVersion() {
        return new SemanticVersion(0, 0, 1);
    }

    @Override
    public SemanticVersion getMaximumVersion() {
        return new SemanticVersion(0, 3, 0);
    }
}
