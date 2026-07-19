package io.github.stefanrichterhuber.wasmtimejavang;

/**
 * 
 * Represents a semantic version.
 * 
 * @param major Major version
 * @param minor Minor version
 * @param patch Patch version
 */
public record SemanticVersion(int major, int minor, int patch) implements Comparable<SemanticVersion> {

    @Override
    public int compareTo(SemanticVersion o) {
        if (major != o.major) {
            return Integer.compare(major, o.major);
        }
        if (minor != o.minor) {
            return Integer.compare(minor, o.minor);
        }
        if (patch != o.patch) {
            return Integer.compare(patch, o.patch);
        }
        return 0;
    }

    /**
     * Returns a string representation of the semantic version.
     * 
     * @return String representation of the semantic version in the format
     *         major.minor.patch
     */
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    /**
     * Parses a semantic version string.
     * Supports formats like 1.2.3, 1.2 (== 1.2.0) or 1 (== 1.0.0)
     *
     * @param version Version string to parse
     * @return SemanticVersion object
     */
    public static SemanticVersion parse(String version) {
        if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }

        String[] parts = version.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        return new SemanticVersion(major, minor, patch);
    }

}
