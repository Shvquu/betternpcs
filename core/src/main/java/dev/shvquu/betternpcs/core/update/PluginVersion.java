package dev.shvquu.betternpcs.core.update;

import java.util.Objects;
import java.util.Optional;

/**
 * A semantic version of the plugin itself, tolerant of the forms real releases come in.
 *
 * <p>{@link dev.shvquu.betternpcs.api.ApiVersion} deliberately accepts only three plain numbers,
 * because it describes a compatibility promise and an ambiguous form there would be a problem. This
 * type has the opposite job: it reads whatever a Git tag or a build says, including the project's
 * own {@code 1.0.0-SNAPSHOT} and the {@code v} prefix GitHub tags conventionally carry.
 *
 * <h2>Pre-releases sort first</h2>
 *
 * <p>Following semantic versioning, {@code 1.0.0-SNAPSHOT} is <em>older</em> than {@code 1.0.0}.
 * That is the rule that makes the update check useful during development: a developer running a
 * snapshot of 1.0.0 is correctly told that the released 1.0.0 is newer, rather than being told
 * nothing because the numbers match.
 *
 * <p>Two pre-releases of the same version compare by their suffix text, which is arbitrary but
 * stable. Nothing depends on getting {@code alpha} versus {@code beta} right; what matters is that
 * the comparison is a total order.
 *
 * <p>Instances are immutable.
 *
 * @param major      the major component
 * @param minor      the minor component
 * @param patch      the patch component
 * @param preRelease the pre-release suffix without its leading hyphen, or {@code null} for a release
 * @since 1.0.0
 */
public record PluginVersion(int major, int minor, int patch, String preRelease)
        implements Comparable<PluginVersion> {

    /**
     * Creates a version.
     *
     * @param major      the major component, not negative
     * @param minor      the minor component, not negative
     * @param patch      the patch component, not negative
     * @param preRelease the pre-release suffix, or {@code null}
     * @throws IllegalArgumentException if any component is negative
     */
    public PluginVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException(
                    "Version components must not be negative: " + major + "." + minor + "." + patch);
        }
        if (preRelease != null && preRelease.isBlank()) {
            preRelease = null;
        }
    }

    /**
     * Parses a version, returning empty rather than throwing.
     *
     * <p>Accepts {@code 1.2.3}, {@code v1.2.3}, {@code 1.2.3-SNAPSHOT}, {@code 1.2} and {@code 1};
     * omitted components are zero. Build metadata after a {@code +} is ignored, as semantic
     * versioning requires.
     *
     * <p>Returns empty rather than throwing because both inputs are outside this code's control:
     * one is a tag somebody typed into GitHub, the other is whatever a fork put in
     * {@code gradle.properties}. Neither is worth an exception on a background thread during an
     * optional update check.
     *
     * @param version the version string, may be {@code null}
     * @return the parsed version, or empty if it is not a version at all
     */
    public static Optional<PluginVersion> parse(String version) {
        if (version == null) {
            return Optional.empty();
        }

        String text = version.trim();
        if (text.startsWith("v") || text.startsWith("V")) {
            // GitHub tags are conventionally "v1.2.3" while the artifact is "1.2.3".
            text = text.substring(1);
        }

        // Build metadata carries no ordering information at all under semantic versioning.
        int build = text.indexOf('+');
        if (build >= 0) {
            text = text.substring(0, build);
        }

        String preRelease = null;
        int hyphen = text.indexOf('-');
        if (hyphen >= 0) {
            preRelease = text.substring(hyphen + 1);
            text = text.substring(0, hyphen);
        }

        String[] parts = text.split("\\.", -1);
        if (parts.length == 0 || parts.length > 3) {
            return Optional.empty();
        }

        int[] numbers = new int[3];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                return Optional.empty();
            }
            try {
                numbers[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException notANumber) {
                return Optional.empty();
            }
            if (numbers[i] < 0) {
                return Optional.empty();
            }
        }

        return Optional.of(new PluginVersion(numbers[0], numbers[1], numbers[2], preRelease));
    }

    /**
     * Returns whether this version is a pre-release.
     *
     * @return {@code true} if a suffix such as {@code SNAPSHOT} or {@code rc1} is present
     */
    public boolean isPreRelease() {
        return preRelease != null;
    }

    /**
     * Returns whether the given version is newer than this one.
     *
     * @param other the version to compare against
     * @return {@code true} if {@code other} should be offered as an update
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public boolean isOlderThan(PluginVersion other) {
        return compareTo(Objects.requireNonNull(other, "other")) < 0;
    }

    @Override
    public int compareTo(PluginVersion other) {
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        if (byMinor != 0) {
            return byMinor;
        }
        int byPatch = Integer.compare(patch, other.patch);
        if (byPatch != 0) {
            return byPatch;
        }

        // Same numbers. A pre-release comes before the release it leads up to, so 1.0.0-SNAPSHOT is
        // older than 1.0.0 — which is exactly what makes the update check useful to a developer
        // running a snapshot build.
        if (preRelease == null && other.preRelease == null) {
            return 0;
        }
        if (preRelease == null) {
            return 1;
        }
        if (other.preRelease == null) {
            return -1;
        }
        return preRelease.compareTo(other.preRelease);
    }

    /**
     * Returns the canonical {@code major.minor.patch[-preRelease]} form, without a {@code v} prefix.
     *
     * @return the version string
     */
    @Override
    public String toString() {
        String base = major + "." + minor + "." + patch;
        return preRelease == null ? base : base + "-" + preRelease;
    }
}
