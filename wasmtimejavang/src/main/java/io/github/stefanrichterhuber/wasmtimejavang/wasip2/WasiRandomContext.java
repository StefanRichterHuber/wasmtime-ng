package io.github.stefanrichterhuber.wasmtimejavang.wasip2;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.Set;

import io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion;
import io.github.stefanrichterhuber.wasmtimejavang.WasmComponentContext;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;

/**
 * Implementation of the {@code wasi:random/*} interfaces (WASI Preview 2,
 * 0.2.6): random, insecure, and insecure-seed -- the {@code "wasi-random"}
 * component context.
 */
public class WasiRandomContext implements WasmComponentContext {

    /** The stable name other contexts reference via {@code getDependencies()}. */
    public static final String NAME = "wasi-random";

    private static final String WASI_RANDOM_RANDOM = "wasi:random/random";
    private static final String WASI_RANDOM_INSECURE = "wasi:random/insecure";
    private static final String WASI_RANDOM_INSECURE_SEED = "wasi:random/insecure-seed";

    private static final Set<String> PROVIDED_INTERFACES = Set.of(
            WASI_RANDOM_RANDOM, WASI_RANDOM_INSECURE, WASI_RANDOM_INSECURE_SEED);
    private SemanticVersion version = WasiCliContext.DEFAULT_VERSION;

    private Random secureRandom;
    private Random insecureRandom;

    /**
     * Creates a new random context with default secure and insecure generators.
     */
    public WasiRandomContext() {
        this.secureRandom = new SecureRandom();
        this.insecureRandom = new Random();
    }

    public WasiRandomContext withRandom(Random random) {
        this.insecureRandom = random;
        return this;
    }

    public WasiRandomContext withSecureRandom(Random secureRandom) {
        this.secureRandom = secureRandom;
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
    public List<ComponentImportFunction> getImportFunctions() {
        return List.of(
                new ComponentImportFunction(WASI_RANDOM_RANDOM + "@" + version, "get-random-bytes",
                        this::getRandomBytes),
                new ComponentImportFunction(WASI_RANDOM_RANDOM + "@" + version, "get-random-u64", this::getRandomU64),
                new ComponentImportFunction(WASI_RANDOM_INSECURE + "@" + version, "get-insecure-random-bytes",
                        this::getInsecureRandomBytes),
                new ComponentImportFunction(WASI_RANDOM_INSECURE + "@" + version, "get-insecure-random-u64",
                        this::getInsecureRandomU64),
                new ComponentImportFunction(WASI_RANDOM_INSECURE_SEED + "@" + version, "insecure-seed",
                        this::insecureSeed));
    }

    @Override
    public List<ComponentImportResource> getImportResources() {
        return List.of();
    }

    protected Object[] getRandomBytes(WasmtimeComponentInstance instance, Object[] args) {
        long len = (Long) args[0];
        byte[] bytes = new byte[(int) len];
        this.secureRandom.nextBytes(bytes);
        return new Object[] { bytes };
    }

    protected Object[] getRandomU64(WasmtimeComponentInstance instance, Object[] args) {
        return new Object[] { this.secureRandom.nextLong() };
    }

    protected Object[] getInsecureRandomBytes(WasmtimeComponentInstance instance, Object[] args) {
        long len = (Long) args[0];
        byte[] bytes = new byte[(int) len];
        this.insecureRandom.nextBytes(bytes);
        return new Object[] { bytes };
    }

    protected Object[] getInsecureRandomU64(WasmtimeComponentInstance instance, Object[] args) {
        return new Object[] { this.insecureRandom.nextLong() };
    }

    protected Object[] insecureSeed(WasmtimeComponentInstance instance, Object[] args) {
        long seed1 = this.insecureRandom.nextLong();
        long seed2 = this.insecureRandom.nextLong();
        // Returns a tuple<u64, u64>, which is represented as a single return value
        // containing an Object array.
        return new Object[] { new Object[] { seed1, seed2 } };
    }

    @Override
    public WasiRandomContext withVersion(SemanticVersion version) {
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
