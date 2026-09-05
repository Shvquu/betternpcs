package dev.shvquu.betternpcs.api;

import java.util.Objects;

/**
 * The semantic version of the BetterNPCs API a running plugin implements.
 *
 * <p>The API follows <a href="https://semver.org/">semantic versioning</a> independently of the
 * plugin version:
 *
 * <ul>
 *   <li><b>major</b> — incremented only for source- or binary-incompatible changes. A plugin built
 *       against a different major version must not be loaded.</li>
 *   <li><b>minor</b> — incremented when API is added. A plugin built against an older minor version
 *       of the same major version keeps working.</li>
 *   <li><b>patch</b> — incremented for behaviour fixes that change no signature.</li>
 * </ul>
 *
 * <p>Dependent plugins that use API added after 1.0.0 should guard startup with
 * {@link #isCompatibleWith(int, int)} so that an outdated BetterNPCs produces a clear message rather
 * than a {@code NoSuchMethodError} at an arbitrary later point.
 *
 * @param major the major component
 * @param minor the minor component
 * @param patch the patch component
 * @since 1.0.0
 */
public record ApiVersion(int major, int minor, int patch) implements Comparable<ApiVersion> {

    /** The API version described by this artifact. */
    public static final ApiVersion CURRENT = new ApiVersion(1, 0, 0);

    /**
     * Creates an API version.
     *
     * @param major the major component, not negative
     * @param minor the minor component, not negative
     * @param patch the patch component, not negative
     * @throws IllegalArgumentException if any component is negative
     */
    public ApiVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException(
                    "Version components must not be negative: " + major + "." + minor + "." + patch);
        }
    }

    /**
     * Parses a {@code major.minor.patch} string.
     *
     * @param version the version string
     * @return the parsed version
     * @throws NullPointerException     if {@code version} is {@code null}
     * @throws IllegalArgumentException if {@code version} is not three dot-separated numbers
     */
    public static ApiVersion parse(String version) {
        Objects.requireNonNull(version, "version");
        String[] parts = version.trim().split("\\.", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Not an API version: '" + version + "'");
        }
        try {
            return new ApiVersion(
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("Not an API version: '" + version + "'", notANumber);
        }
    }

    /**
     * Returns whether this version can serve a plugin written against {@code requiredMajor.requiredMinor}.
     *
     * <p>That is the case when the major components match exactly and this minor component is at
     * least the required one.
     *
     * @param requiredMajor the major version the dependent plugin was built against
     * @param requiredMinor the minor version the dependent plugin was built against
     * @return {@code true} if this version is compatible
     */
    public boolean isCompatibleWith(int requiredMajor, int requiredMinor) {
        return major == requiredMajor && minor >= requiredMinor;
    }

    @Override
    public int compareTo(ApiVersion other) {
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        return byMinor != 0 ? byMinor : Integer.compare(patch, other.patch);
    }

    /**
     * Returns the {@code major.minor.patch} form.
     *
     * @return the dotted version string
     */
    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
