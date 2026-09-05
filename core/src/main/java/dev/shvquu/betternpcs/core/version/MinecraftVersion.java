package dev.shvquu.betternpcs.core.version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A parsed, comparable Minecraft version.
 *
 * <p>Minecraft has used two incompatible version schemes. Everything up to and including 1.21.11
 * used {@code 1.MAJOR.PATCH}. From 26.1 onwards Mojang switched to {@code YEAR.DROP.HOTFIX}, so the
 * release after {@code 1.21.11} is {@code 26.1}. Any code that detects versions by looking for a
 * leading {@code "1."}, or that compares version strings lexically, is wrong on both counts —
 * lexically {@code "1.21.4"} sorts after {@code "1.21.11"}, and {@code "26.2"} does not start with
 * {@code "1."} at all.
 *
 * <p>Instances are immutable. Trailing zero components are insignificant: {@code 1.21} and
 * {@code 1.21.0} are equal and compare equal.
 */
public final class MinecraftVersion implements Comparable<MinecraftVersion> {

    /** Which of Mojang's two numbering schemes a version belongs to. */
    public enum Scheme {
        /** {@code 1.MAJOR.PATCH}, used up to and including 1.21.11. */
        LEGACY,
        /** {@code YEAR.DROP.HOTFIX}, introduced with 26.1. */
        DROP
    }

    private final List<Integer> components;
    private final Scheme scheme;
    private final String raw;

    private MinecraftVersion(List<Integer> components, Scheme scheme, String raw) {
        this.components = components;
        this.scheme = scheme;
        this.raw = raw;
    }

    /**
     * Parses a Minecraft version string such as {@code 1.21.4}, {@code 26.2} or {@code 26.1.2}.
     *
     * <p>A pre-release suffix is tolerated and ignored, so {@code 1.21.9-rc1} parses as
     * {@code 1.21.9}; {@link #raw()} still reports the original text.
     *
     * @param version the version string, typically from {@code Bukkit.getMinecraftVersion()}
     * @return the parsed version
     * @throws NullPointerException     if {@code version} is {@code null}
     * @throws IllegalArgumentException if {@code version} is not a dot-separated list of numbers
     */
    public static MinecraftVersion parse(String version) {
        Objects.requireNonNull(version, "version");

        String trimmed = version.trim();
        int suffix = trimmed.indexOf('-');
        String numeric = suffix >= 0 ? trimmed.substring(0, suffix) : trimmed;

        if (numeric.isEmpty()) {
            throw new IllegalArgumentException("Not a Minecraft version: '" + version + "'");
        }

        String[] parts = numeric.split("\\.", -1);
        List<Integer> parsed = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty() || !isAllDigits(part)) {
                throw new IllegalArgumentException("Not a Minecraft version: '" + version + "'");
            }
            try {
                parsed.add(Integer.parseInt(part));
            } catch (NumberFormatException overflow) {
                throw new IllegalArgumentException("Version component out of range: '" + version + "'", overflow);
            }
        }

        // Trailing zeros carry no meaning: 1.21 and 1.21.0 are the same release.
        while (parsed.size() > 1 && parsed.get(parsed.size() - 1) == 0) {
            parsed.remove(parsed.size() - 1);
        }

        // The legacy scheme is exactly the versions whose major component is 1. Everything else is
        // a year-based drop; this stays correct when the year rolls over to 27, 28 and beyond.
        Scheme scheme = parsed.get(0) == 1 ? Scheme.LEGACY : Scheme.DROP;

        return new MinecraftVersion(Collections.unmodifiableList(parsed), scheme, version);
    }

    private static boolean isAllDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the numeric components with insignificant trailing zeros removed.
     *
     * @return an immutable list holding at least one element
     */
    public List<Integer> components() {
        return components;
    }

    /**
     * Returns which numbering scheme this version uses.
     *
     * @return the scheme
     */
    public Scheme scheme() {
        return scheme;
    }

    /**
     * Returns the string this version was parsed from, including any pre-release suffix.
     *
     * @return the original, untrimmed input
     */
    public String raw() {
        return raw;
    }

    private int componentAt(int index) {
        return index < components.size() ? components.get(index) : 0;
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        // Every legacy version predates every drop version, regardless of the numbers involved:
        // 1.21.11 shipped before 26.1 even though 26 > 1 would also give the right answer here,
        // comparing schemes first keeps that independent of Mojang's future numbering.
        int bySchemeOrder = Integer.compare(scheme.ordinal(), other.scheme.ordinal());
        if (bySchemeOrder != 0) {
            return bySchemeOrder;
        }

        int length = Math.max(components.size(), other.components.size());
        for (int i = 0; i < length; i++) {
            int byComponent = Integer.compare(componentAt(i), other.componentAt(i));
            if (byComponent != 0) {
                return byComponent;
            }
        }
        return 0;
    }

    /**
     * Returns whether this version is within {@code [minInclusive, maxExclusive)}.
     *
     * @param minInclusive lower bound, included
     * @param maxExclusive upper bound, excluded
     * @return {@code true} if this version falls inside the range
     */
    public boolean isWithin(MinecraftVersion minInclusive, MinecraftVersion maxExclusive) {
        return compareTo(minInclusive) >= 0 && compareTo(maxExclusive) < 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof MinecraftVersion version
                && scheme == version.scheme
                && components.equals(version.components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, components);
    }

    /**
     * Returns the normalised version, for example {@code 1.21.9} for an input of {@code 1.21.9-rc1}.
     *
     * @return the dot-separated normalised form
     */
    @Override
    public String toString() {
        return components.stream().map(String::valueOf).collect(Collectors.joining("."));
    }
}
