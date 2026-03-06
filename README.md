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

Include the dependency into your project `pom.xml` (not yet published on maven central): 

```xml
<dependency>
    <groupId>io.github.stefanrichterhuber</groupId>
    <artifactId>wasmtimejavang</artifactId>
    <version>1.0.0-SNAPSHOT</version>
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
    linker.importFunction("env", "hello", List.of(), List.of(), (instance, context, params) -> {
        System.out.println("Java side: " + context.get("greeting"));
        return new long[] {};
    });

    try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
        instance.invoke("run", List.of());
    }
}
```

### Memory Interaction
The `WasmtimeMemory` class provides efficient access to the WASM linear memory by mapping it directly to a `java.nio.ByteBuffer`.

```java
try (WasmtimeInstance instance = new WasmtimeInstance(store, module, linker)) {
    WasmtimeMemory mem = instance.getMemory("memory");

    // Read 4 bytes from offset 0x1000
    byte[] data = mem.read(0x1000, 4);
        
    // Write a single byte to offset 0x1003
    mem.write(0x1003, (byte) 5);

    // Higher level memory interactions. Usually use some kind of native alloc /
    // dealloc allocate memory locations prior to use
    mem.writeCString(0x1000, "Hello", StandardCharsets.UTF_8);
    assertEquals("Hello", mem.readCString(0x1000, StandardCharsets.UTF_8));

    mem.writeString(0x1000, "World", StandardCharsets.UTF_8);
    assertEquals("World", mem.readString(0x1000, "Hello".getBytes().length, StandardCharsets.UTF_8));

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
        instance.invoke("_start", List.of());
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
    * `withSocketFactory(WasiSocketFactory)`: Customizable socket creation (e.g., proxies, virtual networks).

## Architecture

The project is structured into three distinct layers:

1. **Java API**: A type-safe wrapper that represents Wasmtime concepts (Engine, Module, Store, Instance) in Java. It uses `jar-jni` for automatic native library loading. Log4j2 is used as logging framework.
2. **JNI Layer (Rust)**: Built using the [jni-rs](https://crates.io/crates/jni) crate. It manages the lifecycle of native Wasmtime objects and facilitates high-performance data exchange between the JVM and Rust. This layer also redirects Rust `log` crate output to Java's Log4j2. "Classic" Java JNI was preferred over the newer "Foreign Function and Memory API", due to the native library is only planned to be used with Java so it could be tailored to its use. This allows more direct Rust - Java interactions like easily calling Java methods on objects or even create new Java objects using their constructor. A "Foreign Function an Memory API" approach would have resulted in a thinner native layer with far higher implementation effort on the Java side for all the type conversion, especially sacrificing the type and lifetime safety the current rust layer provides for the runtime. 
3. **Wasmtime Core**: The underlying [wasmtime](https://crates.io/crates/wasmtime) Rust crate.

## Comparison to Previous Work

This implementation aims to be more maintainable and feature-complete than earlier attempts:
* [bluejekyll/wasmtime-java](https://github.com/bluejekyll/wasmtime-java)
* [kawamuray/wasmtime-java](https://github.com/kawamuray/wasmtime-java)
