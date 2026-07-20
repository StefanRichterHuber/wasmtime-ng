package io.github.stefanrichterhuber.wasmtimejavang.wasip2wasicli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext.ComponentImportFunction;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext.ComponentImportResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResource;
import io.github.stefanrichterhuber.wasmtimejavang.component.WitResult;
import io.github.stefanrichterhuber.wasmtimejavang.wasip1.ProcExitException;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasicli.WasiCliContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2wasiio.WasiIoContext;

/**
 * Direct unit tests for {@link WasiCliContext}, wiring its {@code "wasi-io"}
 * dependency by hand (a real {@link WasiIoContext}) instead of going through
 * {@code WasmtimeComponentLinker}.
 */
public class WasiCliContextTest {

    private static WasiCliContext newLinkedCli(WasiIoContext io) {
        WasiCliContext cli = new WasiCliContext();
        cli.onDependenciesResolved(
                (name, version) -> WasiIoContext.NAME.equals(name) ? Optional.of(io) : Optional.empty());
        return cli;
    }

    private static ComponentImportFunction functionNamed(List<ComponentImportFunction> functions, String name) {
        return functions.stream().filter(f -> f.funcName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no function named " + name));
    }

    @Test
    public void nameProvidedInterfacesAndDependencies() {
        WasiCliContext cli = new WasiCliContext();
        assertEquals("wasi-cli", cli.name());
        assertEquals(WasiCliContext.NAME, cli.name());
        assertEquals(List.of(WasiIoContext.NAME), cli.getDependencies());

        var provided = cli.getProvidedInterfaces();
        assertTrue(provided.contains("wasi:cli/environment"));
        assertTrue(provided.contains("wasi:cli/exit"));
        assertTrue(provided.contains("wasi:cli/stdin"));
        assertTrue(provided.contains("wasi:cli/stdout"));
        assertTrue(provided.contains("wasi:cli/stderr"));
        assertTrue(provided.contains("wasi:cli/terminal-input"));
        assertTrue(provided.contains("wasi:cli/terminal-output"));
        assertTrue(provided.contains("wasi:cli/terminal-stdin"));
        assertTrue(provided.contains("wasi:cli/terminal-stdout"));
        assertTrue(provided.contains("wasi:cli/terminal-stderr"));
    }

    @Test
    public void onDependenciesResolvedThrowsWhenWasiIoIsMissing() {
        WasiCliContext cli = new WasiCliContext();
        ComponentContextLookup emptyLookup = (name, version) -> Optional.empty();
        assertThrows(IllegalStateException.class, () -> cli.onDependenciesResolved(emptyLookup));
    }

    @Test
    public void builderMethodsReturnThisForChaining() {
        WasiCliContext cli = new WasiCliContext();
        assertSame(cli, cli.withEnvs(Map.of()));
        assertSame(cli, cli.withStdOut(new ByteArrayOutputStream()));
        assertSame(cli, cli.withStdErr(new ByteArrayOutputStream()));
        assertSame(cli, cli.withStdIn(new ByteArrayInputStream(new byte[0])));
        assertSame(cli, cli.withArguments(List.of()));
    }

    @Test
    public void importFunctionsCoverEveryDeclaredMethod() {
        WasiCliContext cli = newLinkedCli(new WasiIoContext());
        List<ComponentImportFunction> functions = cli.getImportFunctions();
        String environment = "wasi:cli/environment@" + cli.getVersion();
        String exit = "wasi:cli/exit@" + cli.getVersion();
        String stdin = "wasi:cli/stdin@" + cli.getVersion();
        String stdout = "wasi:cli/stdout@" + cli.getVersion();
        String stderr = "wasi:cli/stderr@" + cli.getVersion();
        String terminalStdin = "wasi:cli/terminal-stdin@" + cli.getVersion();
        String terminalStdout = "wasi:cli/terminal-stdout@" + cli.getVersion();
        String terminalStderr = "wasi:cli/terminal-stderr@" + cli.getVersion();

        assertTrue(functions.stream().anyMatch(
                f -> f.interfaceName().equals(environment) && f.funcName().equals("get-environment")));
        assertTrue(functions.stream().anyMatch(
                f -> f.interfaceName().equals(environment) && f.funcName().equals("get-arguments")));
        assertTrue(functions.stream().anyMatch(
                f -> f.interfaceName().equals(environment) && f.funcName().equals("initial-cwd")));
        assertTrue(functions.stream()
                .anyMatch(f -> f.interfaceName().equals(exit) && f.funcName().equals("exit")));
        assertTrue(functions.stream()
                .anyMatch(f -> f.interfaceName().equals(stdin) && f.funcName().equals("get-stdin")));
        assertTrue(functions.stream().anyMatch(
                f -> f.interfaceName().equals(stdout) && f.funcName().equals("get-stdout")));
        assertTrue(functions.stream().anyMatch(
                f -> f.interfaceName().equals(stderr) && f.funcName().equals("get-stderr")));
        assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals(terminalStdin)
                && f.funcName().equals("get-terminal-stdin")));
        assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals(terminalStdout)
                && f.funcName().equals("get-terminal-stdout")));
        assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals(terminalStderr)
                && f.funcName().equals("get-terminal-stderr")));
    }

    @Test
    public void importResourcesDelegateToWasiIoForStdio() {
        WasiIoContext io = new WasiIoContext();
        WasiCliContext cli = newLinkedCli(io);
        List<ComponentImportResource> resources = cli.getImportResources();

        ComponentImportResource stdinResource = resources.stream()
                .filter(r -> r.interfaceName().equals("wasi:cli/stdin@" + cli.getVersion())).findFirst()
                .orElseThrow();
        int inRep = io.registerInputStream(new ByteArrayInputStream(new byte[0]));
        stdinResource.destructor().drop(inRep);
        assertNull(io.getInputStream(inRep));

        ComponentImportResource stdoutResource = resources.stream()
                .filter(r -> r.interfaceName().equals("wasi:cli/stdout@" + cli.getVersion())).findFirst()
                .orElseThrow();
        int outRep = io.registerOutputStream(new ByteArrayOutputStream());
        stdoutResource.destructor().drop(outRep);
        assertNull(io.getOutputStream(outRep));

        // terminal-* destructors are documented no-ops: just verify they don't throw.
        resources.stream().filter(r -> r.resourceName().startsWith("terminal-"))
                .forEach(r -> r.destructor().drop(999));
    }

    @Test
    public void getEnvironmentReturnsConfiguredVariables() {
        WasiCliContext cli = newLinkedCli(new WasiIoContext());
        cli.withEnvs(Map.of("FOO", "BAR", "BAZ", "QUX"));

        List<Object> entries = cli.environmentGetEnvironment(null);
        assertEquals(2, entries.size());

        boolean sawFoo = false;
        boolean sawBaz = false;
        for (Object entry : entries) {
            Object[] tuple = (Object[]) entry;
            if (tuple[0].equals("FOO")) {
                assertEquals("BAR", tuple[1]);
                sawFoo = true;
            } else if (tuple[0].equals("BAZ")) {
                assertEquals("QUX", tuple[1]);
                sawBaz = true;
            }
        }
        assertTrue(sawFoo && sawBaz);
    }

    @Test
    public void getEnvironmentIsEmptyByDefault() {
        WasiCliContext cli = newLinkedCli(new WasiIoContext());
        List<Object> entries = cli.environmentGetEnvironment(null);
        assertTrue(entries.isEmpty());
    }

    @Test
    public void getArgumentsReturnsConfiguredArgumentsInOrder() {
        WasiCliContext cli = newLinkedCli(new WasiIoContext());
        cli.withArguments(List.of("prog", "arg1", "arg2"));

        List<Object> arguments = cli.environmentGetArguments(null);
        assertEquals(List.of("prog", "arg1", "arg2"), arguments);
    }

    @Test
    public void getArgumentsIsEmptyByDefault() {
        WasiCliContext cli = newLinkedCli(new WasiIoContext());
        List<Object> arguments = cli.environmentGetArguments(null);
        assertTrue(arguments.isEmpty());
    }

    @Test
    public void initialCwdIsAlwaysEmpty() {
        WasiCliContext cli = newLinkedCli(new WasiIoContext());
        assertEquals(Optional.empty(), cli.environmentInitialCwd(null));
    }

    @Test
    public void exitThrowsProcExitExceptionMappingOkToCodeZero() {
        WasiCliContext cli = newLinkedCli(new WasiIoContext());
        ProcExitException ex = assertThrows(ProcExitException.class,
                () -> cli.exitExit(null, WitResult.ok(null)));
        assertEquals(0, ex.getCode());
    }

    @Test
    public void exitThrowsProcExitExceptionMappingErrToCodeOne() {
        WasiCliContext cli = newLinkedCli(new WasiIoContext());
        ProcExitException ex = assertThrows(ProcExitException.class,
                () -> cli.exitExit(null, WitResult.err(null)));
        assertEquals(1, ex.getCode());
    }

    @Test
    public void getStdinRegistersConfiguredStreamWithWasiIo() {
        WasiIoContext io = new WasiIoContext();
        InputStream configuredStdin = new ByteArrayInputStream(new byte[] { 9 });
        WasiCliContext cli = newLinkedCli(io);
        cli.withStdIn(configuredStdin);

        WitResource resource = cli.stdinGetStdin(null);
        assertEquals("input-stream", resource.resourceName());
        assertTrue(resource.owned());
        assertSame(configuredStdin, io.getInputStream(resource.rep()));
    }

    @Test
    public void getStdoutRegistersConfiguredStreamWithWasiIo() {
        WasiIoContext io = new WasiIoContext();
        OutputStream configuredStdout = new ByteArrayOutputStream();
        WasiCliContext cli = newLinkedCli(io);
        cli.withStdOut(configuredStdout);

        WitResource resource = cli.stdoutGetStdout(null);
        assertEquals("output-stream", resource.resourceName());
        assertTrue(resource.owned());
        assertSame(configuredStdout, io.getOutputStream(resource.rep()));
    }

    @Test
    public void getStderrRegistersConfiguredStreamWithWasiIo() {
        WasiIoContext io = new WasiIoContext();
        OutputStream configuredStderr = new ByteArrayOutputStream();
        WasiCliContext cli = newLinkedCli(io);
        cli.withStdErr(configuredStderr);

        WitResource resource = cli.stderrGetStderr(null);
        assertEquals("output-stream", resource.resourceName());
        assertTrue(resource.owned());
        assertSame(configuredStderr, io.getOutputStream(resource.rep()));
    }

    @Test
    public void terminalDetectionFunctionsAlwaysReportNotATty() {
        WasiCliContext cli = newLinkedCli(new WasiIoContext());
        List<ComponentImportFunction> functions = cli.getImportFunctions();

        for (String funcName : List.of("get-terminal-stdin", "get-terminal-stdout", "get-terminal-stderr")) {
            Object[] result = functionNamed(functions, funcName).function().call(null, new Object[0]);
            assertEquals(Optional.empty(), result[0]);
        }
    }
}
