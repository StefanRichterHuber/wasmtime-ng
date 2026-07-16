package io.github.stefanrichterhuber.wasmtimejavang.wasip2;

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

/**
 * Direct unit tests for {@link WasiCliContext}, wiring its {@code "wasi-io"}
 * dependency by hand (a real {@link WasiIoContext}) instead of going through
 * {@code WasmtimeComponentLinker}.
 */
public class WasiCliContextTest {

    private static WasiCliContext newLinkedCli(WasiIoContext io) {
        WasiCliContext cli = new WasiCliContext();
        cli.onDependenciesResolved(name -> WasiIoContext.NAME.equals(name) ? Optional.of(io) : Optional.empty());
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
        assertTrue(provided.contains("wasi:cli/environment@0.2.6"));
        assertTrue(provided.contains("wasi:cli/exit@0.2.6"));
        assertTrue(provided.contains("wasi:cli/stdin@0.2.6"));
        assertTrue(provided.contains("wasi:cli/stdout@0.2.6"));
        assertTrue(provided.contains("wasi:cli/stderr@0.2.6"));
        assertTrue(provided.contains("wasi:cli/terminal-input@0.2.6"));
        assertTrue(provided.contains("wasi:cli/terminal-output@0.2.6"));
        assertTrue(provided.contains("wasi:cli/terminal-stdin@0.2.6"));
        assertTrue(provided.contains("wasi:cli/terminal-stdout@0.2.6"));
        assertTrue(provided.contains("wasi:cli/terminal-stderr@0.2.6"));
    }

    @Test
    public void onDependenciesResolvedThrowsWhenWasiIoIsMissing() {
        WasiCliContext cli = new WasiCliContext();
        ComponentContextLookup emptyLookup = name -> Optional.empty();
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

        assertTrue(functions.stream().anyMatch(
                f -> f.interfaceName().equals("wasi:cli/environment@0.2.6") && f.funcName().equals("get-environment")));
        assertTrue(functions.stream().anyMatch(
                f -> f.interfaceName().equals("wasi:cli/environment@0.2.6") && f.funcName().equals("get-arguments")));
        assertTrue(functions.stream()
                .anyMatch(f -> f.interfaceName().equals("wasi:cli/exit@0.2.6") && f.funcName().equals("exit")));
        assertTrue(functions.stream()
                .anyMatch(f -> f.interfaceName().equals("wasi:cli/stdin@0.2.6") && f.funcName().equals("get-stdin")));
        assertTrue(functions.stream().anyMatch(
                f -> f.interfaceName().equals("wasi:cli/stdout@0.2.6") && f.funcName().equals("get-stdout")));
        assertTrue(functions.stream().anyMatch(
                f -> f.interfaceName().equals("wasi:cli/stderr@0.2.6") && f.funcName().equals("get-stderr")));
        assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals("wasi:cli/terminal-stdin@0.2.6")
                && f.funcName().equals("get-terminal-stdin")));
        assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals("wasi:cli/terminal-stdout@0.2.6")
                && f.funcName().equals("get-terminal-stdout")));
        assertTrue(functions.stream().anyMatch(f -> f.interfaceName().equals("wasi:cli/terminal-stderr@0.2.6")
                && f.funcName().equals("get-terminal-stderr")));
    }

    @Test
    public void importResourcesDelegateToWasiIoForStdio() {
        WasiIoContext io = new WasiIoContext();
        WasiCliContext cli = newLinkedCli(io);
        List<ComponentImportResource> resources = cli.getImportResources();

        ComponentImportResource stdinResource = resources.stream()
                .filter(r -> r.interfaceName().equals("wasi:cli/stdin@0.2.6")).findFirst().orElseThrow();
        int inRep = io.registerInputStream(new ByteArrayInputStream(new byte[0]));
        stdinResource.destructor().drop(inRep);
        assertNull(io.getInputStream(inRep));

        ComponentImportResource stdoutResource = resources.stream()
                .filter(r -> r.interfaceName().equals("wasi:cli/stdout@0.2.6")).findFirst().orElseThrow();
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

        Object[] result = cli.getEnvironment(null, new Object[0]);
        @SuppressWarnings("unchecked")
        List<Object> entries = (List<Object>) result[0];
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
        Object[] result = cli.getEnvironment(null, new Object[0]);
        @SuppressWarnings("unchecked")
        List<Object> entries = (List<Object>) result[0];
        assertTrue(entries.isEmpty());
    }

    @Test
    public void getArgumentsReturnsConfiguredArgumentsInOrder() {
        WasiCliContext cli = newLinkedCli(new WasiIoContext());
        cli.withArguments(List.of("prog", "arg1", "arg2"));

        Object[] result = cli.getArguments(null, new Object[0]);
        @SuppressWarnings("unchecked")
        List<String> arguments = (List<String>) result[0];
        assertEquals(List.of("prog", "arg1", "arg2"), arguments);
    }

    @Test
    public void getArgumentsIsEmptyByDefault() {
        WasiCliContext cli = newLinkedCli(new WasiIoContext());
        Object[] result = cli.getArguments(null, new Object[0]);
        @SuppressWarnings("unchecked")
        List<String> arguments = (List<String>) result[0];
        assertTrue(arguments.isEmpty());
    }

    @Test
    public void exitThrowsProcExitExceptionMappingOkToCodeZero() {
        WasiCliContext cli = newLinkedCli(new WasiIoContext());
        ProcExitException ex = assertThrows(ProcExitException.class,
                () -> cli.exit(null, new Object[] { WitResult.ok(null) }));
        assertEquals(0, ex.getCode());
    }

    @Test
    public void exitThrowsProcExitExceptionMappingErrToCodeOne() {
        WasiCliContext cli = newLinkedCli(new WasiIoContext());
        ProcExitException ex = assertThrows(ProcExitException.class,
                () -> cli.exit(null, new Object[] { WitResult.err(null) }));
        assertEquals(1, ex.getCode());
    }

    @Test
    public void getStdinRegistersConfiguredStreamWithWasiIo() {
        WasiIoContext io = new WasiIoContext();
        InputStream configuredStdin = new ByteArrayInputStream(new byte[] { 9 });
        WasiCliContext cli = newLinkedCli(io);
        cli.withStdIn(configuredStdin);

        Object[] result = cli.getStdin(null, new Object[0]);
        WitResource resource = (WitResource) result[0];
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

        Object[] result = cli.getStdout(null, new Object[0]);
        WitResource resource = (WitResource) result[0];
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

        Object[] result = cli.getStderr(null, new Object[0]);
        WitResource resource = (WitResource) result[0];
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
