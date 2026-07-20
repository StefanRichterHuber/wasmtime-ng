package io.github.stefanrichterhuber.wasmtimejavang.wasip2wasirandom;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import io.github.stefanrichterhuber.wasmtimejavang.SemanticVersion;
import io.github.stefanrichterhuber.wasmtimejavang.WasmtimeComponentInstance;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasirandom.InsecureContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasirandom.InsecureSeedContext;
import io.github.stefanrichterhuber.wasmtimejavang.wasip2.generated.wasirandom.RandomContext;

/**
 * Implementation of the {@code wasi:random/*} interfaces (WASI Preview 2,
 * 0.2.6): random, insecure, and insecure-seed -- the {@code "wasi-random"}
 * component context.
 * <br>
 * Implements all three generated interfaces at once, since Java allows a
 * class to implement any number of interfaces (unlike extending abstract
 * classes) -- {@code getImportFunctions()}/{@code getImportResources()}/
 * {@code getProvidedInterfaces()} are combined by calling each interface's
 * own default implementation via {@code Interface.super.xxx()}.
 */
public class WasiRandomContext implements RandomContext, InsecureContext, InsecureSeedContext {

    /** The stable name other contexts reference via {@code getDependencies()}. */
    public static final String NAME = "wasi-random";

    private SemanticVersion version = DEFAULT_VERSION;

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
        Set<String> result = new LinkedHashSet<>();
        result.addAll(RandomContext.super.getProvidedInterfaces());
        result.addAll(InsecureContext.super.getProvidedInterfaces());
        result.addAll(InsecureSeedContext.super.getProvidedInterfaces());
        return result;
    }

    @Override
    public List<ComponentImportFunction> getImportFunctions() {
        List<ComponentImportFunction> result = new ArrayList<>();
        result.addAll(RandomContext.super.getImportFunctions());
        result.addAll(InsecureContext.super.getImportFunctions());
        result.addAll(InsecureSeedContext.super.getImportFunctions());
        return result;
    }

    @Override
    public List<ComponentImportResource> getImportResources() {
        List<ComponentImportResource> result = new ArrayList<>();
        result.addAll(RandomContext.super.getImportResources());
        result.addAll(InsecureContext.super.getImportResources());
        result.addAll(InsecureSeedContext.super.getImportResources());
        return result;
    }

    @Override
    public byte[] randomGetRandomBytes(WasmtimeComponentInstance instance, long len) {
        byte[] bytes = new byte[(int) len];
        this.secureRandom.nextBytes(bytes);
        return bytes;
    }

    @Override
    public long randomGetRandomU64(WasmtimeComponentInstance instance) {
        return this.secureRandom.nextLong();
    }

    @Override
    public byte[] insecureGetInsecureRandomBytes(WasmtimeComponentInstance instance, long len) {
        byte[] bytes = new byte[(int) len];
        this.insecureRandom.nextBytes(bytes);
        return bytes;
    }

    @Override
    public long insecureGetInsecureRandomU64(WasmtimeComponentInstance instance) {
        return this.insecureRandom.nextLong();
    }

    @Override
    public Object[] insecureSeedInsecureSeed(WasmtimeComponentInstance instance) {
        long seed1 = this.insecureRandom.nextLong();
        long seed2 = this.insecureRandom.nextLong();
        // Returns a tuple<u64, u64>, represented as a single Object[] element.
        return new Object[] { seed1, seed2 };
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
