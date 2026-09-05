package dev.shvquu.betternpcs.api.npc.property;

import java.util.Objects;
import java.util.Optional;

/**
 * The built-in rules deciding which players an NPC is shown to.
 *
 * <p>Only the two checks that every server needs live here — distance and a permission node — and
 * both are deliberately cheap, because the tracker evaluates them for every player and every nearby
 * NPC many times a second. Anything richer (a region, a party, a quest state) belongs in a
 * {@link dev.shvquu.betternpcs.api.npc.NpcVisibilityRule} registered by an extension, which the
 * tracker consults only after these have passed.
 *
 * <p>Instances are immutable and safe to share across threads.
 *
 * @since 1.0.0
 */
public final class NpcVisibility {

    /**
     * Passed as the view distance to mean "use the server's entity tracking range".
     */
    public static final double SERVER_VIEW_DISTANCE = -1.0;

    /** Visible to everyone within the server's view distance. */
    public static final NpcVisibility DEFAULT = new NpcVisibility(SERVER_VIEW_DISTANCE, null, true);

    private final double viewDistance;
    private final String permission;
    private final boolean visibleByDefault;

    private NpcVisibility(double viewDistance, String permission, boolean visibleByDefault) {
        this.viewDistance = viewDistance;
        this.permission = permission;
        this.visibleByDefault = visibleByDefault;
    }

    /**
     * Returns visibility limited to a distance.
     *
     * @param viewDistance the radius in blocks, or {@link #SERVER_VIEW_DISTANCE}
     * @return the visibility settings
     * @throws IllegalArgumentException if {@code viewDistance} is zero, or negative and not
     *                                  {@link #SERVER_VIEW_DISTANCE}
     */
    public static NpcVisibility withinDistance(double viewDistance) {
        return DEFAULT.withViewDistance(viewDistance);
    }

    /**
     * Returns a copy of these settings with a different view distance.
     *
     * @param newViewDistance the radius in blocks, or {@link #SERVER_VIEW_DISTANCE}
     * @return the derived settings
     * @throws IllegalArgumentException if {@code newViewDistance} is zero, or negative and not
     *                                  {@link #SERVER_VIEW_DISTANCE}
     */
    public NpcVisibility withViewDistance(double newViewDistance) {
        if (newViewDistance != SERVER_VIEW_DISTANCE && !(newViewDistance > 0.0)) {
            throw new IllegalArgumentException(
                    "View distance must be positive or SERVER_VIEW_DISTANCE, was " + newViewDistance);
        }
        return new NpcVisibility(newViewDistance, permission, visibleByDefault);
    }

    /**
     * Returns a copy of these settings requiring a permission node.
     *
     * @param newPermission the node a player must hold, or {@code null} or blank for none
     * @return the derived settings
     */
    public NpcVisibility withPermission(String newPermission) {
        String normalised = newPermission == null || newPermission.isBlank() ? null : newPermission.trim();
        return new NpcVisibility(viewDistance, normalised, visibleByDefault);
    }

    /**
     * Returns a copy of these settings with a different default.
     *
     * @param newVisibleByDefault {@code true} to show the NPC to everyone who passes the other
     *                            checks, {@code false} to show it only to players it was explicitly
     *                            shown to
     * @return the derived settings
     */
    public NpcVisibility withVisibleByDefault(boolean newVisibleByDefault) {
        return new NpcVisibility(viewDistance, permission, newVisibleByDefault);
    }

    /**
     * Returns the radius in blocks within which the NPC is shown.
     *
     * @return the radius, or {@link #SERVER_VIEW_DISTANCE} to defer to the server
     */
    public double viewDistance() {
        return viewDistance;
    }

    /**
     * Returns whether {@link #viewDistance()} defers to the server's tracking range.
     *
     * @return {@code true} if the server's view distance applies
     */
    public boolean usesServerViewDistance() {
        return viewDistance == SERVER_VIEW_DISTANCE;
    }

    /**
     * Returns the permission node a player must hold to see the NPC.
     *
     * @return the node, or empty if no permission is required
     */
    public Optional<String> permission() {
        return Optional.ofNullable(permission);
    }

    /**
     * Returns whether the NPC is shown to players that were not explicitly told about it.
     *
     * <p>Setting this to {@code false} turns the NPC into an opt-in one: it is only rendered for
     * players passed to {@link dev.shvquu.betternpcs.api.npc.Npc#show}. That is how a per-player
     * NPC — a quest giver only the questing player can see — is built.
     *
     * @return {@code true} if visible to everyone by default
     */
    public boolean visibleByDefault() {
        return visibleByDefault;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof NpcVisibility visibility
                && Double.compare(viewDistance, visibility.viewDistance) == 0
                && Objects.equals(permission, visibility.permission)
                && visibleByDefault == visibility.visibleByDefault;
    }

    @Override
    public int hashCode() {
        return Objects.hash(viewDistance, permission, visibleByDefault);
    }

    /**
     * Returns a short description of these settings.
     *
     * @return the description
     */
    @Override
    public String toString() {
        return "NpcVisibility[distance="
                + (usesServerViewDistance() ? "server" : viewDistance)
                + (permission == null ? "" : ", permission=" + permission)
                + (visibleByDefault ? "" : ", opt-in")
                + ']';
    }
}
