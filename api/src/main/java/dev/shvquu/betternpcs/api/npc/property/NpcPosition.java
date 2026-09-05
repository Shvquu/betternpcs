package dev.shvquu.betternpcs.api.npc.property;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * An immutable NPC position: the name of a world plus coordinates and a rotation.
 *
 * <p>A world <em>name</em> rather than a {@link World} handle is stored on purpose. NPC data is
 * loaded before worlds are guaranteed to exist and outlives any particular world instance, so a
 * position must stay meaningful while its world is unloaded — {@link #toLocation()} then simply
 * reports that it cannot be resolved instead of the position becoming corrupt.
 *
 * <p>Rotation is normalised on construction: {@code yaw} is wrapped into {@code [-180, 180)} and
 * {@code pitch} is clamped to {@code [-90, 90]}, which is the range the protocol can represent.
 * Two positions that point in the same direction are therefore always {@link #equals(Object) equal},
 * regardless of how the angles were expressed.
 *
 * @param world the world name, never {@code null} or blank
 * @param x     the x coordinate
 * @param y     the y coordinate
 * @param z     the z coordinate
 * @param yaw   the yaw in degrees, normalised to {@code [-180, 180)}
 * @param pitch the pitch in degrees, clamped to {@code [-90, 90]}
 * @since 1.0.0
 */
public record NpcPosition(String world, double x, double y, double z, float yaw, float pitch) {

    /**
     * Creates a position, normalising the rotation.
     *
     * @param world the world name
     * @param x     the x coordinate
     * @param y     the y coordinate
     * @param z     the z coordinate
     * @param yaw   the yaw in degrees, any value
     * @param pitch the pitch in degrees, any value
     * @throws NullPointerException     if {@code world} is {@code null}
     * @throws IllegalArgumentException if {@code world} is blank, or a coordinate is not finite
     */
    public NpcPosition {
        Objects.requireNonNull(world, "world");
        if (world.isBlank()) {
            throw new IllegalArgumentException("World name must not be blank");
        }
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        yaw = normaliseYaw(yaw);
        pitch = clampPitch(pitch);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Coordinate " + name + " must be finite, was " + value);
        }
    }

    private static float normaliseYaw(float yaw) {
        if (!Float.isFinite(yaw)) {
            throw new IllegalArgumentException("Yaw must be finite, was " + yaw);
        }
        float wrapped = yaw % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        } else if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        // -0.0f and 0.0f are distinct to Float.compare, which record equality uses. Collapse them so
        // that a yaw of 360 and a yaw of 0 really are the same position.
        return wrapped == 0.0f ? 0.0f : wrapped;
    }

    private static float clampPitch(float pitch) {
        if (!Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Pitch must be finite, was " + pitch);
        }
        return Math.clamp(pitch, -90.0f, 90.0f);
    }

    /**
     * Creates a position from a Bukkit location.
     *
     * @param location the location, must have a world
     * @return the equivalent position
     * @throws NullPointerException if {@code location} or its world is {@code null}
     */
    public static NpcPosition of(Location location) {
        Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(location.getWorld(), "location.world");
        return new NpcPosition(
                world.getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
    }

    /**
     * Creates a position with no rotation.
     *
     * @param world the world name
     * @param x     the x coordinate
     * @param y     the y coordinate
     * @param z     the z coordinate
     * @return the position, facing yaw 0 and pitch 0
     */
    public static NpcPosition of(String world, double x, double y, double z) {
        return new NpcPosition(world, x, y, z, 0.0f, 0.0f);
    }

    /**
     * Resolves this position against the currently loaded worlds.
     *
     * @return the location, or empty if the world is not loaded
     */
    public Optional<Location> toLocation() {
        World loaded = Bukkit.getWorld(world);
        return loaded == null ? Optional.empty() : Optional.of(toLocation(loaded));
    }

    /**
     * Resolves this position against a specific world, ignoring the stored world name.
     *
     * @param target the world to place the location in
     * @return the location in {@code target}
     * @throws NullPointerException if {@code target} is {@code null}
     */
    public Location toLocation(World target) {
        Objects.requireNonNull(target, "target");
        return new Location(target, x, y, z, yaw, pitch);
    }

    /**
     * Returns a copy of this position at different coordinates.
     *
     * @param newX the new x coordinate
     * @param newY the new y coordinate
     * @param newZ the new z coordinate
     * @return the moved position
     */
    public NpcPosition withCoordinates(double newX, double newY, double newZ) {
        return new NpcPosition(world, newX, newY, newZ, yaw, pitch);
    }

    /**
     * Returns a copy of this position with a different rotation.
     *
     * @param newYaw   the new yaw in degrees
     * @param newPitch the new pitch in degrees
     * @return the rotated position
     */
    public NpcPosition withRotation(float newYaw, float newPitch) {
        return new NpcPosition(world, x, y, z, newYaw, newPitch);
    }

    /**
     * Returns a copy of this position in a different world.
     *
     * @param newWorld the new world name
     * @return the position in {@code newWorld}
     */
    public NpcPosition withWorld(String newWorld) {
        return new NpcPosition(newWorld, x, y, z, yaw, pitch);
    }

    /**
     * Returns a copy of this position offset by the given deltas.
     *
     * @param deltaX the x offset
     * @param deltaY the y offset
     * @param deltaZ the z offset
     * @return the offset position
     */
    public NpcPosition offset(double deltaX, double deltaY, double deltaZ) {
        return new NpcPosition(world, x + deltaX, y + deltaY, z + deltaZ, yaw, pitch);
    }

    /**
     * Returns whether the given position is in the same world as this one.
     *
     * @param other the position to compare against
     * @return {@code true} if both world names are equal
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public boolean isSameWorld(NpcPosition other) {
        return world.equals(Objects.requireNonNull(other, "other").world());
    }

    /**
     * Returns the squared distance to another position in the same world.
     *
     * <p>Squared distance avoids a square root, which matters because visibility tracking evaluates
     * this for every player and every NPC on every tick.
     *
     * @param other the position to measure to
     * @return the squared distance, or {@link Double#MAX_VALUE} if the worlds differ
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public double distanceSquaredTo(NpcPosition other) {
        if (!isSameWorld(other)) {
            return Double.MAX_VALUE;
        }
        double deltaX = x - other.x;
        double deltaY = y - other.y;
        double deltaZ = z - other.z;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    /**
     * Returns the squared distance to a Bukkit location.
     *
     * @param location the location to measure to
     * @return the squared distance, or {@link Double#MAX_VALUE} if the worlds differ or the location
     *         has no world
     * @throws NullPointerException if {@code location} is {@code null}
     */
    public double distanceSquaredTo(Location location) {
        Objects.requireNonNull(location, "location");
        World locationWorld = location.getWorld();
        if (locationWorld == null || !world.equals(locationWorld.getName())) {
            return Double.MAX_VALUE;
        }
        double deltaX = x - location.getX();
        double deltaY = y - location.getY();
        double deltaZ = z - location.getZ();
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    /**
     * Returns a compact human-readable form such as {@code world @ 100.00, 64.00, 100.00 (90.0/0.0)}.
     *
     * @return the formatted position
     */
    @Override
    public String toString() {
        // Locale.ROOT, not the JVM default: this string ends up in log lines and in messages, and a
        // server running with a German locale would otherwise print "100,50" where an English one
        // prints "100.50".
        return String.format(
                Locale.ROOT, "%s @ %.2f, %.2f, %.2f (%.1f/%.1f)", world, x, y, z, yaw, pitch);
    }
}
