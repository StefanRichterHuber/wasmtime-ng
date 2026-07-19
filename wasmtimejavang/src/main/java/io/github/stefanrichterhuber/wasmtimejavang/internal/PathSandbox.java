package io.github.stefanrichterhuber.wasmtimejavang.internal;

import java.nio.file.Path;

/**
 * Resolves a guest-relative path against a preopened host base directory,
 * rejecting anything that would escape it (e.g. via {@code ..} segments or an
 * absolute path) -- shared by the WASI Preview 1 ({@code wasip1}) and
 * Preview 2 ({@code wasip2}) filesystem implementations, which otherwise use
 * unrelated ABIs (raw linear-memory pointers vs. Component Model values) and
 * so don't share much else.
 */
public final class PathSandbox {

    private PathSandbox() {
    }

    /**
     * Resolves {@code path} against {@code base}, normalizing it and
     * verifying the result is still contained within {@code base}.
     *
     * @param base The preopened host directory a guest path is relative to.
     * @param path The guest-supplied (possibly relative, possibly malicious)
     *             path string.
     * @return The resolved host {@link Path}, or {@code null} if {@code base}
     *         is {@code null} or the resolved path would escape it.
     */
    public static Path resolve(Path base, String path) {
        if (base == null) {
            return null;
        }
        if (path.isEmpty() || path.equals(".")) {
            return base;
        }

        Path resolved = base.resolve(path).normalize().toAbsolutePath();
        Path absoluteBase = base.normalize().toAbsolutePath();

        if (resolved.startsWith(absoluteBase)) {
            return resolved;
        }
        return null;
    }
}
