package io.github.stefanrichterhuber.wasmtimejavang.wasip2;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup;
import io.github.stefanrichterhuber.wasmtimejavang.ComponentFunction;
import io.github.stefanrichterhuber.wasmtimejavang.ResourceDestructor;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.NoOpInputStream;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.NoOpOutputStream;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.ProcExitException;

/**
 * Implementation of the {@code wasi:cli/*} interfaces (WASI Preview 2,
 * 0.2.6): environment, exit, stdio, and terminal-* stubs -- the
 * {@code "wasi-cli"} component context.
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
public class WasiCliContext implements WasmComponentContext {
    private static final Logger LOGGER = LogManager.getLogger();

    /** The stable name other contexts reference via {@code getDependencies()}. */
    public static final String NAME = "wasi-cli";

    private static final String WASI_CLI_ENVIRONMENT = "wasi:cli/environment@0.2.6";
    private static final String WASI_CLI_EXIT = "wasi:cli/exit@0.2.6";
    private static final String WASI_CLI_STDIN = "wasi:cli/stdin@0.2.6";
    private static final String WASI_CLI_STDOUT = "wasi:cli/stdout@0.2.6";
    private static final String WASI_CLI_STDERR = "wasi:cli/stderr@0.2.6";
    private static final String WASI_CLI_TERMINAL_INPUT = "wasi:cli/terminal-input@0.2.6";
    private static final String WASI_CLI_TERMINAL_OUTPUT = "wasi:cli/terminal-output@0.2.6";
    private static final String WASI_CLI_TERMINAL_STDIN = "wasi:cli/terminal-stdin@0.2.6";
    private static final String WASI_CLI_TERMINAL_STDOUT = "wasi:cli/terminal-stdout@0.2.6";
    private static final String WASI_CLI_TERMINAL_STDERR = "wasi:cli/terminal-stderr@0.2.6";
    private static final Set<String> PROVIDED_INTERFACES = Set.of(
            WASI_CLI_ENVIRONMENT, WASI_CLI_EXIT, WASI_CLI_STDIN, WASI_CLI_STDOUT, WASI_CLI_STDERR,
            WASI_CLI_TERMINAL_INPUT, WASI_CLI_TERMINAL_OUTPUT,
            WASI_CLI_TERMINAL_STDIN, WASI_CLI_TERMINAL_STDOUT, WASI_CLI_TERMINAL_STDERR);

    private final Map<String, String> env = new HashMap<>();
    private final List<String> args = new ArrayList<>();
    private InputStream stdin = new NoOpInputStream();
    private OutputStream stdout = new NoOpOutputStream();
    private OutputStream stderr = new NoOpOutputStream();

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
     * Sets the command-line arguments exposed via {@code wasi:cli/environment#get-arguments}.
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
        return PROVIDED_INTERFACES;
    }

    @Override
    public List<String> getDependencies() {
        return List.of(WasiIoContext.NAME);
    }

    @Override
    public void onDependenciesResolved(ComponentContextLookup lookup) {
        this.io = (WasiIoResources) lookup.resolve(WasiIoContext.NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "\"" + NAME + "\" requires a \"" + WasiIoContext.NAME + "\" dependency implementing "
                                + WasiIoResources.class.getSimpleName()));
    }

    @Override
    public List<ComponentImportFunction> getImportFunctions() {
        List<ComponentImportFunction> result = new ArrayList<>();
        result.add(func(WASI_CLI_ENVIRONMENT, "get-environment", this::getEnvironment));
        result.add(func(WASI_CLI_ENVIRONMENT, "get-arguments", this::getArguments));
        result.add(func(WASI_CLI_EXIT, "exit", this::exit));
        result.add(func(WASI_CLI_STDIN, "get-stdin", this::getStdin));
        result.add(func(WASI_CLI_STDOUT, "get-stdout", this::getStdout));
        result.add(func(WASI_CLI_STDERR, "get-stderr", this::getStderr));
        result.add(func(WASI_CLI_TERMINAL_STDIN, "get-terminal-stdin", this::none));
        result.add(func(WASI_CLI_TERMINAL_STDOUT, "get-terminal-stdout", this::none));
        result.add(func(WASI_CLI_TERMINAL_STDERR, "get-terminal-stderr", this::none));
        return result;
    }

    @Override
    public List<ComponentImportResource> getImportResources() {
        return List.of(
                resource(WASI_CLI_STDIN, "input-stream", io::dropInputStream),
                resource(WASI_CLI_STDOUT, "output-stream", io::dropOutputStream),
                resource(WASI_CLI_STDERR, "output-stream", io::dropOutputStream),
                resource(WASI_CLI_TERMINAL_INPUT, "terminal-input", this::dropNoop),
                resource(WASI_CLI_TERMINAL_OUTPUT, "terminal-output", this::dropNoop),
                resource(WASI_CLI_TERMINAL_STDIN, "terminal-input", this::dropNoop),
                resource(WASI_CLI_TERMINAL_STDOUT, "terminal-output", this::dropNoop),
                resource(WASI_CLI_TERMINAL_STDERR, "terminal-output", this::dropNoop));
    }

    private static ComponentImportFunction func(String interfaceName, String funcName, ComponentFunction function) {
        return new ComponentImportFunction(interfaceName, funcName, function);
    }

    private static ComponentImportResource resource(String interfaceName, String resourceName,
            ResourceDestructor destructor) {
        return new ComponentImportResource(interfaceName, resourceName, destructor);
    }

    private void dropNoop(int rep) {
        // terminal-input/terminal-output are never actually constructed by
        // this implementation (get-terminal-* always answers "not a tty"),
        // so there is nothing to release.
    }

    private Object[] none(WasmtimeComponentInstance instance, Object[] args) {
        return new Object[] { Optional.empty() };
    }

    protected Object[] getEnvironment(WasmtimeComponentInstance instance, Object[] args) {
        List<Object> entries = new ArrayList<>();
        for (Map.Entry<String, String> e : env.entrySet()) {
            entries.add(new Object[] { e.getKey(), e.getValue() });
        }
        return new Object[] { entries };
    }

    protected Object[] getArguments(WasmtimeComponentInstance instance, Object[] callArgs) {
        return new Object[] { List.copyOf(this.args) };
    }

    protected Object[] exit(WasmtimeComponentInstance instance, Object[] args) {
        WitResult status = (WitResult) args[0];
        LOGGER.debug("Wasm component called wasi:cli/exit with ok={}", status.ok());
        throw new ProcExitException(status.ok() ? 0 : 1);
    }

    protected Object[] getStdin(WasmtimeComponentInstance instance, Object[] args) {
        int rep = io.registerInputStream(stdin);
        return new Object[] { WitResource.own("input-stream", rep) };
    }

    protected Object[] getStdout(WasmtimeComponentInstance instance, Object[] args) {
        int rep = io.registerOutputStream(stdout);
        return new Object[] { WitResource.own("output-stream", rep) };
    }

    protected Object[] getStderr(WasmtimeComponentInstance instance, Object[] args) {
        int rep = io.registerOutputStream(stderr);
        return new Object[] { WitResource.own("output-stream", rep) };
    }
}
