package io.github.stefanrichterhuber.wasmtimejavang.wasip2;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.Set;

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

    private static final String WASI_RANDOM_RANDOM = "wasi:random/random@0.2.6";
    private static final String WASI_RANDOM_INSECURE = "wasi:random/insecure@0.2.6";
    private static final String WASI_RANDOM_INSECURE_SEED = "wasi:random/insecure-seed@0.2.6";
    
    private static final Set<String> PROVIDED_INTERFACES = Set.of(
            WASI_RANDOM_RANDOM, WASI_RANDOM_INSECURE, WASI_RANDOM_INSECURE_SEED);

    private final SecureRandom secureRandom;
    private final Random insecureRandom;

    /**
     * Creates a new random context with default secure and insecure generators.
     */
    public WasiRandomContext() {
        this(new SecureRandom(), new Random());
    }

    /**
     * Creates a new random context with custom secure and insecure generators.
     *
     * @param secureRandom   The secure random generator.
     * @param insecureRandom The insecure random generator.
     */
    public WasiRandomContext(SecureRandom secureRandom, Random insecureRandom) {
        this.secureRandom = secureRandom;
        this.insecureRandom = insecureRandom;
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
                new ComponentImportFunction(WASI_RANDOM_RANDOM, "get-random-bytes", this::getRandomBytes),
                new ComponentImportFunction(WASI_RANDOM_RANDOM, "get-random-u64", this::getRandomU64),
                new ComponentImportFunction(WASI_RANDOM_INSECURE, "get-insecure-random-bytes", this::getInsecureRandomBytes),
                new ComponentImportFunction(WASI_RANDOM_INSECURE, "get-insecure-random-u64", this::getInsecureRandomU64),
                new ComponentImportFunction(WASI_RANDOM_INSECURE_SEED, "insecure-seed", this::insecureSeed));
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
        // Returns a tuple<u64, u64>, which is represented as a single return value containing an Object array.
        return new Object[] { new Object[] { seed1, seed2 } };
    }
}
