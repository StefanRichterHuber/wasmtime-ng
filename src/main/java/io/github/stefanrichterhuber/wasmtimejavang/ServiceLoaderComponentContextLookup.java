package io.github.stefanrichterhuber.wasmtimejavang;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Default {@link ComponentContextLookup}: discovers every
 * {@link WasmComponentContext} registered as a {@link ServiceLoader} provider
 * of the {@code WasmComponentContext} service type (via
 * {@code META-INF/services/io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext}
 * files, or module {@code provides ... with ...} declarations) and returns
 * the first one whose {@link WasmComponentContext#name()} matches.
 * <br>
 * A provider class needs a public no-arg constructor, or (Java 9+) a public
 * static {@code provider()} factory method.
 * <br>
 * <b>Why {@code ServiceLoader} and not something else:</b> it is a JDK-native
 * mechanism for exactly this "discover an implementation from the classpath"
 * problem, with zero additional runtime dependency -- in keeping with this
 * library's existing minimal footprint (only {@code jar-jni} and
 * {@code log4j} at runtime). The alternatives considered:
 * <ul>
 * <li>Classpath-scanning libraries (Reflections, ClassGraph) offer more power
 * (e.g. discovery by annotation) but pull in a whole new dependency to solve
 * a problem the JDK already solves for a handful of known context names.</li>
 * <li>A full DI framework (Guice, Spring, CDI) is the wrong layer entirely
 * for a native-bridge library, and would force every consumer to adopt a
 * container just to use WASI support.</li>
 * <li>JDK-internal SPI patterns predating {@code ServiceLoader} (e.g.
 * {@code DriverManager}-style static registries) are strictly less capable
 * and have themselves largely been superseded by {@code ServiceLoader}.</li>
 * </ul>
 * Callers that want something else entirely (explicit wiring, Spring, ...)
 * can implement {@link ComponentContextLookup} directly and register it as a
 * {@code META-INF/services/io.github.stefanrichterhuber.wasmtimejavang.ComponentContextLookup}
 * provider -- {@link WasmtimeComponentLinker} resolves its own dependency
 * lookup strategy the same SPI way, falling back to this class if none is
 * registered. See also {@link RegistryComponentContextLookup} for an explicit
 * alternative already provided by this library.
 */
public class ServiceLoaderComponentContextLookup implements ComponentContextLookup {

    @Override
    public Optional<WasmComponentContext> resolve(String name, SemanticVersion version) {
        for (WasmComponentContext context : ServiceLoader.load(WasmComponentContext.class)) {
            if (name.equals(context.name())) {
                if (!context.supportsVersion(version)) {
                    continue;
                }
                context.withVersion(version);
                return Optional.of(context);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<WasmComponentContext> resolveProviding(String interfaceName) {
        // Extract version and name from the interface name
        String[] parts = interfaceName.split("@");
        if (parts.length != 2) {
            return Optional.empty();
        }
        String name = parts[0];
        SemanticVersion version = SemanticVersion.parse(parts[1]);

        for (WasmComponentContext context : ServiceLoader.load(WasmComponentContext.class)) {
            if (context.getProvidedInterfaces().contains(name)) {
                if (!context.supportsVersion(version)) {
                    continue;
                }
                context.withVersion(version);

                return Optional.of(context);
            }
        }
        return Optional.empty();
    }
}
