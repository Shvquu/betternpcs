package dev.shvquu.betternpcs.core.version;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Chooses the {@link VersionAdapter} implementation that matches the running Minecraft version.
 *
 * <p>Every adapter is compiled against a different Paper dev bundle and therefore links against
 * classes that only exist on its own Minecraft version. They can coexist in one jar only because
 * nothing references them statically: this resolver loads the single matching class by name, so the
 * other five are never verified by the JVM.
 *
 * <p>Versions outside the registered ranges are reported as unsupported rather than approximated.
 * Falling back to the newest adapter would produce a plugin that starts cleanly and then sends
 * packets the client cannot parse — a much harder failure to diagnose than a refusal at startup.
 */
public final class VersionAdapterResolver {

    private static final String ADAPTER_PACKAGE = "dev.shvquu.betternpcs.nms.";

    /**
     * A half-open Minecraft version range mapped to the adapter class that serves it.
     *
     * @param minInclusive     lowest version this adapter supports, included
     * @param maxExclusive     first version this adapter no longer supports, excluded
     * @param adapterClassName fully qualified name of the {@link VersionAdapter} implementation
     */
    public record Registration(
            MinecraftVersion minInclusive,
            MinecraftVersion maxExclusive,
            String adapterClassName) {

        /** @param minInclusive lower bound
         *  @param maxExclusive upper bound
         *  @param adapterClassName adapter class
         */
        public Registration {
            Objects.requireNonNull(minInclusive, "minInclusive");
            Objects.requireNonNull(maxExclusive, "maxExclusive");
            Objects.requireNonNull(adapterClassName, "adapterClassName");
            if (minInclusive.compareTo(maxExclusive) >= 0) {
                throw new IllegalArgumentException(
                        "Empty version range: [" + minInclusive + ", " + maxExclusive + ")");
            }
        }

        @Override
        public String toString() {
            return "[" + minInclusive + ", " + maxExclusive + ")";
        }
    }

    private final List<Registration> registrations;

    private VersionAdapterResolver(List<Registration> registrations) {
        this.registrations = List.copyOf(registrations);
    }

    /**
     * Returns a resolver covering every Minecraft version this build ships an adapter for.
     *
     * <p>Ranges are grouped, not one-per-patch: consecutive Minecraft releases whose packet surface
     * is identical share a module rather than duplicating its code.
     *
     * @return the default resolver
     */
    public static VersionAdapterResolver withDefaults() {
        return new VersionAdapterResolver(List.of(
                registration("1.21.4", "1.21.5", "v1_21_4", "V1_21_4Adapter"),
                registration("1.21.5", "1.21.6", "v1_21_5", "V1_21_5Adapter"),
                registration("1.21.6", "1.21.9", "v1_21_8", "V1_21_8Adapter"),
                registration("1.21.9", "26.1", "v1_21_11", "V1_21_11Adapter"),
                registration("26.1", "26.2", "v26_1", "V26_1Adapter"),
                registration("26.2", "26.3", "v26_2", "V26_2Adapter")));
    }

    /**
     * Returns a resolver backed by the given registrations, for testing.
     *
     * @param registrations the ranges to serve, ordered ascending and non-overlapping
     * @return a resolver over those registrations
     */
    public static VersionAdapterResolver of(List<Registration> registrations) {
        return new VersionAdapterResolver(registrations);
    }

    private static Registration registration(String min, String max, String module, String simpleName) {
        return new Registration(
                MinecraftVersion.parse(min),
                MinecraftVersion.parse(max),
                ADAPTER_PACKAGE + module + "." + simpleName);
    }

    /**
     * Returns the registrations this resolver serves, ordered ascending.
     *
     * @return an immutable list
     */
    public List<Registration> registrations() {
        return registrations;
    }

    /**
     * Finds the registration covering the given Minecraft version.
     *
     * @param version the running Minecraft version
     * @return the matching registration, or empty if the version is not supported
     */
    public Optional<Registration> resolve(MinecraftVersion version) {
        Objects.requireNonNull(version, "version");
        return registrations.stream()
                .filter(candidate -> version.isWithin(candidate.minInclusive(), candidate.maxExclusive()))
                .findFirst();
    }

    /**
     * Loads and instantiates the adapter for the given Minecraft version.
     *
     * @param version the running Minecraft version
     * @return the adapter instance
     * @throws UnsupportedMinecraftVersionException if no adapter covers {@code version}, or if the
     *                                              matching adapter class cannot be loaded
     */
    public VersionAdapter load(MinecraftVersion version) throws UnsupportedMinecraftVersionException {
        Registration registration = resolve(version)
                .orElseThrow(() -> new UnsupportedMinecraftVersionException(version, supportedRangeDescription()));

        try {
            Class<?> type = Class.forName(registration.adapterClassName());
            return type.asSubclass(VersionAdapter.class).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError failure) {
            // Reaching here means the jar was built without this adapter, or the adapter does not
            // link against the server actually running. Both are packaging bugs, not user error.
            throw new UnsupportedMinecraftVersionException(
                    version,
                    "adapter " + registration.adapterClassName() + " could not be loaded",
                    failure);
        }
    }

    /**
     * Returns a human-readable summary of the supported versions, for error messages.
     *
     * @return a description such as {@code "1.21.4 - 26.2"}
     */
    public String supportedRangeDescription() {
        if (registrations.isEmpty()) {
            return "none";
        }
        MinecraftVersion lowest = registrations.get(0).minInclusive();
        // The upper bound is exclusive, so report the newest version we actually serve.
        Registration highest = registrations.get(registrations.size() - 1);
        return lowest + " - " + highest.minInclusive();
    }
}
