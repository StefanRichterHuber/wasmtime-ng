[![CI](https://github.com/StefanRichterHuber/wasmtime-ng/actions/workflows/maven.yml/badge.svg?branch=main)](https://github.com/StefanRichterHuber/wasmtime-ng/actions/workflows/maven.yml)

# Wasmtime-java-ng

> **Disclaimer:** This project is an independent community effort and is not affiliated with, maintained, or endorsed by the original [Wasmtime project](https://github.com/bytecodealliance/wasmtime) or the Bytecode Alliance.

Wasmtime-java-ng provides Java bindings for [Wasmtime](https://github.com/bytecodealliance/wasmtime), a fast, secure, and highly configurable WebAssembly runtime.

This project allows Java applications to execute WebAssembly modules, interact with linear memory, and leverage the WebAssembly System Interface (WASI) through a native bridge built on Rust.

## Prerequisites

Building this project requires both Rust and Java toolchains:

* **Java 21+** and **Maven**
* **Rust** and **Cargo**
* **[cross-rs](https://github.com/cross-rs/cross)** and **Docker** (required for cross-platform native builds)

## Building

The project uses Maven to orchestrate both the Java compilation and the Rust native build. To build the entire project for Linux x86_64:

```shell
mvn clean install 
```

### Multi-Platform Builds

By default, only the native library for Linux x86_64 is built. You can target specific platforms using Maven profiles:

* `build-linux-x86-64`: Linux x86_64
* `build-linux-aarch64`: Linux Arm64
* `build-windows-x86_64`: Windows x86_64

To build a JAR containing native libraries for all supported platforms:

```shell
mvn -P build-linux-x86-64,build-linux-aarch64,build-windows-x86_64 clean install
```

For production environments, the `release` profile optimizes the Rust binaries for performance and size:

```shell
mvn -P release,build-linux-x86-64,build-linux-aarch64,build-windows-x86_64 clean install
```

## Usage

Include the dependency into your project `pom.xml` (the published artifact contains native libraries for Linux x86_64, Linux aarch64 and Windows x86_64):

```xml
<dependency>
    <groupId>io.github.stefanrichterhuber</groupId>
    <artifactId>wasmtimejavang</artifactId>
    <version>[Current Version]</version>
</dependency>
```


### Function Imports

You can import Java methods into the WebAssembly context, allowing WASM modules to invoke native Java code.

```java
String wat = """
    (module
      (func $hello (import "env" "hello"))
      (func (export "run") (call $hello))
    )
    """;

try (
    WasmtimeEngine engine = new WasmtimeEngine();
    WasmtimeModule module = new WasmtimeModule(engine, wat);
    WasmtimeStore store = new WasmtimeStore(engine);
    WasmtimeLinker linker = new WasmtimeLinker(engine, store)
) {
    store.getContext().put("greeting", "Hello world");

    // Define a Java function for the WASM module to call
    linker.importFunction("env", "hello", List.of(), List.of(), (instance, params) -> {
        System.out.println("Java side: " + instance.getContext().get("greeting"));
        return new Object[] {};
    });

    try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
        instance.invoke("run");
    }
}
```

### Supported WASM Types

All type conversions are bi-directional if not mentioned otherwise.

| Wasm type | Java type | Comment |
| :--- | :--- | :---: |
| **`I32`** | `java.lang.Integer` | Numbers are always boxed |
| **`I64`** | `java.lang.Long` | Numbers are always boxed |
| **`F32`** | `java.lang.Float` | Numbers are always boxed |
| **`F64`** | `java.lang.Double` | Numbers are always boxed |
| **`V128`** | `io.github.stefanrichterhuber.wasmtimejavang.V128` | V128 is a wrapper around `byte[16]`. Build in conversion from /to `byte[]`, `short[]`, `int[]`, `long[]` and `BigInteger` |
| **`FuncRef`** | `io.github.stefanrichterhuber.wasmtimejavang.WasmtimeFunction` | Callable function reference. Currently export from wasm to java supported |
| **`ExternRef`** | any java object | Any java object can be passed through the wasm runtime as extern ref |

### Memory Interaction

The `WasmtimeMemory` class provides efficient access to the WASM linear memory by mapping it directly to a `java.nio.ByteBuffer`.

```java
try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
    WasmtimeMemory mem = instance.getMemory("memory");

    // Read 4 bytes from offset 0x1000
    byte[] data = mem.read(0x1000, 4);
        
    // Write a single byte to offset 0x1003
    mem.write(0x1003, (byte) 5);

    // Higher level memory interactions. Usually one would call some kind of native alloc
    // function to allocate memory locations prior to use
    mem.writeCString(0x1000, "Hello", StandardCharsets.UTF_8);
    assertEquals("Hello", mem.readCString(0x1000, StandardCharsets.UTF_8));

    mem.writeString(0x1000, "World", StandardCharsets.UTF_8);
    assertEquals("World", mem.readString(0x1000, "World".getBytes().length, StandardCharsets.UTF_8));

    mem.writeLong(0x1000, Long.MAX_VALUE);
    assertEquals(Long.MAX_VALUE, mem.readLong(0x1000));

    mem.writeInt(0x1000, Integer.MAX_VALUE);
    assertEquals(Integer.MAX_VALUE, mem.readInt(0x1000));

    mem.writeShort(0x1000, Short.MAX_VALUE);
    assertEquals(Short.MAX_VALUE, mem.readShort(0x1000));

    mem.write(0x1000, Byte.MAX_VALUE);
    assertEquals(Byte.MAX_VALUE, mem.readByte(0x1000));

    // Grow to memory to have another page
    mem.grow(1);
}
```

## WASI Snapshot Preview 1

While Wasmtime provides built-in WASI support, this library implements WASI Preview 1 directly on the Java side (`WasiPI1Context`). This approach reduces indirection and allows WASM modules to interact directly with Java objects, such as `InputStream`, `OutputStream`, and virtual filesystems.

### Virtualized Filesystem

To protect the host system, you can use a virtual filesystem like [JimFS](https://github.com/google/jimfs) to provide a sandboxed environment for the WASM module.

```java
try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
    Path root = fs.getPath("/");
    Files.writeString(root.resolve("input.txt"), "Data for WASM");

    WasiPI1Context wasi = new WasiPI1Context()
        .withDirectory(root, ".") // Map virtual root to WASM current directory
        .withStdOut(System.out);

    linker.link(wasi);
    
    try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
        instance.start();
    }
}
```

### Supported WASI Functions

| Category | Functions | Status | Configuration |
| :--- | :--- | :---: | :--- |
| **Arguments** | `args_get`, `args_sizes_get` | ✅ | `withArguments(List<String>)` |
| **Environment** | `environ_get`, `environ_sizes_get` | ✅ | `withEnvs(Map<String, String>)` |
| **Clocks** | `clock_res_get`, `clock_time_get` | ✅ | `withClock(Clock)` |
| **Random** | `random_get` | ✅ | `withRandom(Random)` |
| **Process** | `proc_exit` | ✅ | Throws `ProcExitException` |
| | `proc_raise` | 🟡 | Returns `NOSYS` (Stub) |
| **Scheduler** | `sched_yield` | ✅ | Calls `Thread.yield()` |
| **Filesystem** | Full FD and Path operations | ✅ | `withDirectory(Path, String)` |
| **Network** | `sock_accept`, `sock_recv`, `sock_send`, `sock_shutdown` | ✅ | `withSocket()`, `withServerSocket()` |
| **Polling** | `poll_oneoff` | 🟡 | Clock events supported |

### Configuration Options

`WasiPI1Context` provides a fluent API for resource virtualization:

* **Standard I/O**: `withStdIn(InputStream)`, `withStdOut(OutputStream)`, `withStdErr(OutputStream)`.
* **Filesystem**: `withDirectory(Path host, String client)` to map host/virtual paths.
* **Networking**:
    * `withSocket(java.net.Socket)`: Pre-opens a client socket.
    * `withServerSocket(java.net.ServerSocket)`: Pre-opens a listening socket.

### WASI threads

There is some preliminary support for WASI threads using the additional context `WasiThreadContext`.

```java
 try (FileInputStream fis = new FileInputStream(wasmPath.toFile());
        WasmtimeEngine engine = new WasmtimeEngine();
        WasmtimeModule module = new WasmtimeModule(engine, fis);
        WasmtimeStore store = new WasmtimeStore(engine);
        WasmtimeLinker linker = new WasmtimeLinker(engine, store);
        // A shared memory is necessary to share memory between threads
        WasmtimeSharedMemory sharedMemory = new WasmtimeSharedMemory(engine, 2, 256)) {

    WasiPI1Context wasiContext = new WasiPI1Context()
            .withArguments(List.of("wasip1threadtest"))
            .withStdOut(System.out)
            .withStdErr(System.err);

    WasiThreadContext threadContext = new WasiThreadContext();

    linker.defineSharedMemory("env", "memory", sharedMemory);
    linker.link(wasiContext);
    linker.link(threadContext);

    try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
         instance.start();
    }

    // Give some time for threads to finish and print
    Thread.sleep(1000);
}

```

> **Info:** As of now, nightly toolchain is required to properly build rust wasm wasip1 apps with thread support!

## WASI Preview 2 / Component Model (`wasm32-wasip2`)

Alongside core-module WASI Preview 1, this library supports the [Component Model](https://component-model.bytecodealliance.org/) (`wasm32-wasip2` binaries) — again with WASI implemented on the Java side rather than delegated to `wasmtime-wasi`. It's built from three pieces:

* `WasmtimeComponent` — compiles a component, and can introspect it *without instantiating it*: `getImportInterfaces()` / `getExportInterfaces()` read the interfaces a component needs/exports straight from the compiled binary, and `isCommand()` reports whether it exports `wasi:cli/run` (i.e. is runnable as a command, as opposed to e.g. a service with no entry point).
* `WasmtimeComponentLinker` — resolves imports, mirroring `WasmtimeLinker` but for the richer Component Model value/resource model.
* `WasmtimeComponentInstance` — an instantiated component; exported functions are invoked dynamically by interface + function name via `invoke(...)`, or via the `asCliRunnable()` convenience for a `wasi:cli/run` command.

```java
try (
    FileInputStream fis = new FileInputStream("hello.wasm");
    WasmtimeEngine engine = new WasmtimeEngine();
    WasmtimeComponent component = new WasmtimeComponent(engine, fis);
    WasmtimeStore store = new WasmtimeStore(engine);
    WasmtimeComponentLinker linker = new WasmtimeComponentLinker(engine, store)
) {
    // Link an explicitly-configured context (captures stdout)...
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    linker.linkContext(new WasiCliContext().withStdOut(stdout).withArguments(List.of("hello")));

    // ...then auto-link whatever else the component actually needs (wasi:clocks,
    // wasi:random, ...), discovered straight off the compiled component.
    linker.linkRequired(component);

    try (WasmtimeComponentInstance instance = new WasmtimeComponentInstance(store, component, linker)) {
        instance.asCliRunnable().call(); // invokes wasi:cli/run#run
    }
}
```

`linkRequired` only fills in interfaces nothing has already been linked for, so `linkContext(...)` calls made beforehand always take precedence — this is also the mechanism for overriding built-in behavior (see below).

### Supported WASI Preview 2 interfaces

Each interface group lives in its own `WasmComponentContext` implementation under the `wasip2` package, and all six are auto-discovered by `linkRequired(...)` with zero configuration.

| Interfaces | Class | Status | Configuration |
| :--- | :--- | :---: | :--- |
| `wasi:cli/environment,exit,stdin,stdout,stderr,terminal-*` | `WasiCliContext` | ✅ | `withEnvs(Map)`, `withArguments(List)`, `withStdIn/StdOut/StdErr(...)`. `terminal-*` always reports "not a tty" |
| `wasi:clocks/monotonic-clock,wall-clock` | `WasiClocksContext` | ✅ | none (uses `System.nanoTime()`/`System.currentTimeMillis()`) |
| `wasi:random/random,insecure,insecure-seed` | `WasiRandomContext` | ✅ | `withSecureRandom(Random)`, `withRandom(Random)` (the insecure generator) |
| `wasi:io/poll,streams,error` | `WasiIoContext` | 🟡 | Owns the shared stream/pollable tables other contexts (`WasiCliContext`, `WasiClocksContext`, `WasiFilesystemContext`, `WasiSocketsContext`) depend on. Both blocking and non-blocking reads are implemented (`[method]input-stream.blocking-read` and `.read`, plus the batch `poll` function), but not `skip`/`blocking-skip` |
| `wasi:filesystem/types,preopens` | `WasiFilesystemContext` | 🟡 | `withDirectory(Path host, String client)` (repeatable). No symlink support (`symlink-at`/`readlink-at` not implemented, `path-flags.symlink-follow` ignored — host paths always resolve following symlinks) |
| `wasi:sockets/network,instance-network,tcp(-create-socket),udp(-create-socket),ip-name-lookup` | `WasiSocketsContext` | 🟡 | None — a guest creates/binds/connects/listens its own sockets, same as on a real host. IPv4 only (`ipv6` rejected as `not-supported`). Some options `java.net` has no equivalent for (TCP `hop-limit`, TCP keep-alive idle-time/interval/count, UDP `unicast-hop-limit`) are stored and returned as configured but not actually applied to the OS socket |

`WasiFilesystemContext` mirrors WASI Preview 1's filesystem support (`WasiPI1Context` + `withDirectory`): preopened host directories are exposed as `descriptor` resources sandboxed the same way -- a guest path can never resolve outside its preopened directory, no matter how many `..` segments it contains. Reads/writes go through `wasi:io/streams` the same way `wasi:cli/stdout` does: `[method]descriptor.read-via-stream`/`write-via-stream`/`append-via-stream` hand out `input-stream`/`output-stream` resources from the shared `wasi-io` table (so it depends on `"wasi-io"` the same way `WasiCliContext` and `WasiClocksContext` do), rather than reading/writing bytes directly.

```java
try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix().toBuilder()
        .setAttributeViews("basic", "owner", "posix", "unix").build())) {
    Path root = fs.getPath("/");
    Files.writeString(root.resolve("input.txt"), "Data for WASM");

    linker.linkContext(new WasiCliContext().withStdOut(System.out));
    linker.linkContext(new WasiFilesystemContext().withDirectory(root, ".")); // preopen "." -> root
    linker.linkRequired(component);

    try (WasmtimeComponentInstance instance = new WasmtimeComponentInstance(store, component, linker)) {
        instance.asCliRunnable().call();
    }
}
```

Unlike `WasiFilesystemContext`, `WasiSocketsContext` needs no configuration at all: WASI Preview 2 gives a guest full control over its own sockets (create, bind, connect/listen, accept, send/receive), so there's no host-side preopen step to wire up -- linking it is enough, and a guest creating a TCP or UDP socket behaves exactly as it would talking to a real OS network stack (loopback and outbound connections both work). The two-phase `start-x`/`finish-x` operations WASI defines for non-blocking bind/connect/listen are collapsed into one blocking `java.net` call apiece internally, since every host call in this bridge is already synchronous from the guest's perspective.

```java
linker.linkContext(new WasiCliContext().withStdOut(System.out));
linker.linkContext(new WasiSocketsContext());
linker.linkRequired(component); // pulls in wasi-io, wasi-clocks, etc. as needed
```

### Implementing your own component context

This is the mechanism that matters most for actually using this library: WASI is just the *built-in* set of `WasmComponentContext` implementations, and any Java application can define its own to expose custom host functionality — database access, business logic, whatever — to a component's imports, the exact same way. This section walks through a complete, real, runnable example (its full source is in the repo and covered by `WasmtimeCustomComponentTest`, so it's guaranteed to stay in sync with the code, not just aspirational documentation).

**1. Define the interface in WIT.** This is the contract the guest and the host agree on — a Java-side `WasmComponentContext` needs no WIT file itself (it's built against the fully dynamic `component::Val` API), but the *component being compiled* does, since Rust needs it to generate typed bindings. [`src/test/rust/wasip2customtest/wit/world.wit`](src/test/rust/wasip2customtest/wit/world.wit):

```wit
package my:custom@1.0.0;

interface greet {
    hello: func(name: string) -> string;
    add: func(a: u32, b: u32) -> u32;
}

world custom-world {
    import greet;
}
```

**2. Consume it from the guest.** Any language with Component Model tooling works; the test fixture is Rust using [`wit-bindgen`](https://github.com/bytecodealliance/wit-bindgen) (the only test fixture in this repo that needs it — every WASI-only fixture relies solely on `wasm32-wasip2`'s built-in componentization). [`src/test/rust/wasip2customtest/src/main.rs`](src/test/rust/wasip2customtest/src/main.rs):

```rust
wit_bindgen::generate!({
    world: "custom-world",
    path: "wit",
});

use my::custom::greet;

fn main() {
    let greeting = greet::hello("Wasmtime-Java");
    println!("GREETING={}", greeting);

    let sum = greet::add(19, 23);
    println!("SUM={}", sum);
}
```

**3. Implement `WasmComponentContext` in Java** to provide `hello`/`add`. [`GreetComponentContext`](src/test/java/io/github/stefanrichterhuber/wasmtimejavang/GreetComponentContext.java):

```java
public class GreetComponentContext implements WasmComponentContext {
    private static final String INTERFACE = "my:custom/greet";

    private SemanticVersion version = new SemanticVersion(1, 0, 0);

    @Override
    public String name() { return "greet"; } // stable id, referenced by getDependencies()

    @Override
    public List<ComponentImportFunction> getImportFunctions() {
        String versioned = INTERFACE + "@" + version;
        return List.of(
                new ComponentImportFunction(versioned, "hello", this::hello),
                new ComponentImportFunction(versioned, "add", this::add));
    }

    @Override
    public List<ComponentImportResource> getImportResources() { return List.of(); }

    @Override
    public Set<String> getProvidedInterfaces() { return Set.of(INTERFACE); } // see "Auto-discovery" below

    private Object[] hello(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { "Hello, " + (String) args[0] + "!" };
    }

    private Object[] add(WasmtimeComponentInstance instance, Object... args) {
        return new Object[] { (Integer) args[0] + (Integer) args[1] };
    }

    @Override
    public WasmComponentContext withVersion(SemanticVersion version) { this.version = version; return this; }

    @Override
    public SemanticVersion getVersion() { return version; }
}
```

Argument/return types follow the value bridge described above — here just `String` and `Integer` (WIT `u32`), but the same context could just as well take/return a `Map` (`record`), `List`/`Object[]` (`list`/`tuple`), `byte[]` (`list<u8>`), or a resource.

**4. Link it and run.** [`WasmtimeCustomComponentTest`](src/test/java/io/github/stefanrichterhuber/wasmtimejavang/WasmtimeCustomComponentTest.java):

```java
try (FileInputStream fis = new FileInputStream(wasmPath);
        WasmtimeEngine engine = new WasmtimeEngine();
        WasmtimeComponent component = new WasmtimeComponent(engine, fis);
        WasmtimeStore store = new WasmtimeStore(engine);
        WasmtimeComponentLinker linker = new WasmtimeComponentLinker(engine, store)) {

    linker.linkContext(new WasiCliContext().withStdOut(System.out));
    linker.linkContext(new GreetComponentContext());
    linker.linkRequired(component); // pulls in wasi-io for stdout; nothing to do for "my:custom/greet"

    try (WasmtimeComponentInstance instance = new WasmtimeComponentInstance(store, component, linker)) {
        instance.asCliRunnable().call(); // prints "GREETING=Hello, Wasmtime-Java!" / "SUM=42"
    }
}
```

`linker.linkContext(...)` links a context explicitly and unconditionally; `linkRequired(component)` only fills in interfaces *nothing has already claimed* (see above), so the two compose freely — explicit calls always take precedence.

**Depending on another context.** To share state with another context (e.g. `WasiIoContext`'s stream table), declare it by bare name in `getDependencies()` and resolve it in `onDependenciesResolved(ComponentContextLookup)` — `WasmtimeComponentLinker` links declared dependencies first and guarantees they're available by the time it's called:

```java
@Override
public List<String> getDependencies() { return List.of(WasiIoContext.NAME); }

@Override
public void onDependenciesResolved(ComponentContextLookup lookup) {
    this.io = (WasiIoResources) lookup.resolve(WasiIoContext.NAME, getVersion()).orElseThrow();
}
```

**Auto-discovery.** To make a context discoverable by `linkRequired(component)` instead of linking it explicitly, declare the (bare, version-independent) interface names it implements via `getProvidedInterfaces()` (as `GreetComponentContext` already does above) and register the class as a `WasmComponentContext` [`ServiceLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html) provider (a `META-INF/services/io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext` file listing the class, which needs a public no-arg constructor or a public static `provider()` method) — this is exactly how the six built-in `Wasi*Context` classes register themselves.

**Watch out:** `getProvidedInterfaces()` must be overridden even for a context that's *always* linked explicitly via `linkContext(...)` and never registered for `ServiceLoader` discovery — if that same call site also calls `linkRequired(...)` afterwards (as step 4 does), `linkRequired` has no other way to know the explicitly-linked context already satisfies the interface, and raises `IllegalStateException` for it. This is why `GreetComponentContext` overrides `getProvidedInterfaces()` above even though it's never meant to be auto-discovered.

### Overriding a built-in WASI Preview 2 context

None of the `Wasi*Context` classes are `final`, and the built-ins are only ever *auto-linked* — never forced — so there are two ways to override one:

1. **Per-linker, explicit**: call `linker.linkContext(...)` with your own instance (a subclass, or an unrelated implementation) using the *same* `name()` (e.g. `"wasi-cli"`) before calling `linkRequired(...)`. Since `linkRequired` only auto-links interfaces nothing has claimed yet, your explicitly-linked context wins and the built-in provider is never consulted.
2. **Global, via lookup strategy**: register your own `ComponentContextLookup` (e.g. a `RegistryComponentContextLookup` populated with your preferred instances, or a custom implementation) as a `META-INF/services/io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup` provider. `WasmtimeComponentLinker` resolves its dependency-lookup strategy the same SPI way, falling back to `ServiceLoaderComponentContextLookup` (which is what discovers the built-ins) only if nothing else is registered — so this replaces resolution for every linker in the process, not just one.

### Version negotiation

Every `WasmComponentContext` tracks a `SemanticVersion` (`withVersion`/`getVersion`), used to build the actual versioned interface name (e.g. `"wasi:cli/environment@0.2.6"`) each import is registered under — `getProvidedInterfaces()` itself stays version-independent (bare names), so lookup/auto-discovery doesn't need to know a component's exact required version up front. `getMiniumVersion()`/`getMaximumVersion()` (defaulted to accept anything) bound what `supportsVersion(...)` — and therefore `ComponentContextLookup.resolve(...)` — accepts; the built-in WASI contexts default to `0.2.6` (what `cargo build --target wasm32-wasip2` on current stable Rust actually emits) and accept `[0.0.1, 0.3.0]`, so they're ready for the eventual WASI 0.3 interfaces without code changes once toolchains catch up, but can be pinned with `.withVersion(...)` if a component needs something else within that range.

## Architecture

The project is structured into three distinct layers:

1. **Java API**: A type-safe wrapper that represents Wasmtime concepts (Engine, Module, Store, Instance) in Java. It uses `jar-jni` for automatic native library loading. Log4j2 is used as logging framework.
2. **JNI Layer (Rust)**: Built using the [jni-rs](https://crates.io/crates/jni) crate. It manages the lifecycle of native Wasmtime objects and facilitates data exchange between the JVM and Rust. This layer also redirects Rust `log` crate output to Java's Log4j2. "Classic" Java JNI was preferred over the newer "Foreign Function and Memory API", due to the native library is only planned to be used with Java so it could be tailored to its use. This allows more direct Rust - Java interactions like easily calling Java methods on objects or even create new Java objects using their constructor. A "Foreign Function an Memory API" approach would have resulted in a thinner native layer with far higher implementation effort on the Java side for all the type conversion, especially sacrificing the type and lifetime safety the current rust layer provides for the runtime.
3. **Wasmtime Core**: The underlying [wasmtime](https://crates.io/crates/wasmtime) Rust crate.

All modules are tested using JUnit. Test coverage is measured with Jacoco. The neccessary wasm modules for testing are build with rust (see `src/test/rust`). Using `exec-maven-plugin` these wasm modules are build in the `generate-test-resources` phase.

## Comparison to Previous Work

This implementation aims to be more maintainable and feature-complete than earlier attempts:

* [bluejekyll/wasmtime-java](https://github.com/bluejekyll/wasmtime-java)
* [kawamuray/wasmtime-java](https://github.com/kawamuray/wasmtime-java)
