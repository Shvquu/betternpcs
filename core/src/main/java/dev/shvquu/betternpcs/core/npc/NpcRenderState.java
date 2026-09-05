package dev.shvquu.betternpcs.core.npc;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * The part of an NPC that exists only while it is spawned: its entity ids, who can currently see it,
 * and the per-player overrides.
 *
 * <p>Split out of {@link NpcHandle} because the two have different lifetimes and different rules.
 * Everything here is discarded on despawn and rebuilt on spawn, and none of it is ever persisted —
 * an entity id written to a database would be meaningless on the next start.
 *
 * <p>Not thread safe. Every field is touched by the tracker on the main thread; the one exception is
 * {@link #entityId()}, which the registry reads from the network thread and which is therefore
 * volatile.
 *
 * @since 1.0.0
 */
public final class NpcRenderState {

    /** The entity id of an NPC that is not spawned. */
    public static final int NO_ENTITY_ID = -1;

    private volatile int entityId = NO_ENTITY_ID;
    private UUID entityUuid;
    private String profileName;

    private final Set<Player> viewers = new LinkedHashSet<>();
    private final Map<UUID, Boolean> overrides = new LinkedHashMap<>();

    /** Entity ids of the nametag lines, in render order. */
    private int[] nametagIds = new int[0];

    /** Entity ids of the hologram lines, in render order. */
    private int[] hologramIds = new int[0];

    private final Map<UUID, Float> lastSentYaw = new LinkedHashMap<>();

    /**
     * Returns the entity id clients know this NPC by.
     *
     * @return the entity id, or {@link #NO_ENTITY_ID} if the NPC is not spawned
     */
    public int entityId() {
        return entityId;
    }

    /**
     * Sets the entity id.
     *
     * @param value the new id, or {@link #NO_ENTITY_ID} to clear it
     */
    public void entityId(int value) {
        this.entityId = value;
    }

    /**
     * Returns the uuid used for the NPC's entity and, for a player NPC, its profile.
     *
     * @return the uuid, or {@code null} if the NPC is not spawned
     */
    public UUID entityUuid() {
        return entityUuid;
    }

    /**
     * Sets the entity uuid.
     *
     * @param value the new uuid, or {@code null} to clear it
     */
    public void entityUuid(UUID value) {
        this.entityUuid = value;
    }

    /**
     * Returns the name in a player NPC's profile.
     *
     * @return the profile name, or {@code null} if the NPC is not spawned
     */
    public String profileName() {
        return profileName;
    }

    /**
     * Sets the profile name.
     *
     * @param value the new profile name, or {@code null} to clear it
     */
    public void profileName(String value) {
        this.profileName = value;
    }

    /**
     * Returns the players the NPC is currently rendered for.
     *
     * @return the live viewer set; iterate it only on the main thread
     */
    public Set<Player> viewers() {
        return viewers;
    }

    /**
     * Returns an immutable copy of the viewer set.
     *
     * @return the viewers
     */
    public List<Player> viewerSnapshot() {
        return List.copyOf(viewers);
    }

    /**
     * Records that a player is now being sent the NPC.
     *
     * @param player the player
     * @return {@code true} if the player was not already a viewer
     */
    public boolean addViewer(Player player) {
        return viewers.add(Objects.requireNonNull(player, "player"));
    }

    /**
     * Records that a player is no longer being sent the NPC.
     *
     * @param player the player
     * @return {@code true} if the player was a viewer
     */
    public boolean removeViewer(Player player) {
        Objects.requireNonNull(player, "player");
        lastSentYaw.remove(player.getUniqueId());
        return viewers.remove(player);
    }

    /**
     * Returns whether a player is currently being sent the NPC.
     *
     * @param player the player
     * @return {@code true} if the player is a viewer
     */
    public boolean isViewer(Player player) {
        return viewers.contains(Objects.requireNonNull(player, "player"));
    }

    /**
     * Sets a per-player visibility override.
     *
     * @param player  the player
     * @param visible {@code true} to always show, {@code false} to always hide
     */
    public void override(Player player, boolean visible) {
        overrides.put(Objects.requireNonNull(player, "player").getUniqueId(), visible);
    }

    /**
     * Clears a per-player visibility override.
     *
     * @param player the player
     */
    public void clearOverride(Player player) {
        overrides.remove(Objects.requireNonNull(player, "player").getUniqueId());
    }

    /**
     * Returns a player's visibility override.
     *
     * @param player the player
     * @return {@link Boolean#TRUE} to always show, {@link Boolean#FALSE} to always hide, or
     *         {@code null} when the NPC's normal rules apply
     */
    public Boolean overrideFor(Player player) {
        return overrides.get(Objects.requireNonNull(player, "player").getUniqueId());
    }

    /**
     * Returns the entity ids of the nametag lines.
     *
     * @return the ids, empty if no nametag is rendered
     */
    public int[] nametagIds() {
        return nametagIds.clone();
    }

    /**
     * Sets the entity ids of the nametag lines.
     *
     * @param ids the ids
     */
    public void nametagIds(int[] ids) {
        this.nametagIds = ids.clone();
    }

    /**
     * Returns the entity ids of the hologram lines.
     *
     * @return the ids, empty if no hologram is rendered
     */
    public int[] hologramIds() {
        return hologramIds.clone();
    }

    /**
     * Sets the entity ids of the hologram lines.
     *
     * @param ids the ids
     */
    public void hologramIds(int[] ids) {
        this.hologramIds = ids.clone();
    }

    /**
     * Returns every entity id this NPC currently owns, its own and its text lines'.
     *
     * @return the ids, empty if the NPC is not spawned
     */
    public int[] allEntityIds() {
        int total = (entityId == NO_ENTITY_ID ? 0 : 1) + nametagIds.length + hologramIds.length;
        int[] combined = new int[total];
        int index = 0;
        if (entityId != NO_ENTITY_ID) {
            combined[index++] = entityId;
        }
        System.arraycopy(nametagIds, 0, combined, index, nametagIds.length);
        index += nametagIds.length;
        System.arraycopy(hologramIds, 0, combined, index, hologramIds.length);
        return combined;
    }

    /**
     * Returns the yaw last sent to a viewer.
     *
     * <p>Look tracking recomputes a rotation every couple of ticks, and most of the time the answer
     * is the one already on the client. Remembering it turns "send a packet per viewer per interval"
     * into "send one only when the NPC actually turned", which is the bulk of the saving.
     *
     * @param player the viewer
     * @return the last yaw sent, or {@code null} if none has been
     */
    public Float lastSentYaw(Player player) {
        return lastSentYaw.get(Objects.requireNonNull(player, "player").getUniqueId());
    }

    /**
     * Records the yaw sent to a viewer.
     *
     * @param player the viewer
     * @param yaw    the yaw that was sent
     */
    public void lastSentYaw(Player player, float yaw) {
        lastSentYaw.put(Objects.requireNonNull(player, "player").getUniqueId(), yaw);
    }

    /**
     * Discards everything, returning the NPC to its unspawned state.
     */
    public void reset() {
        entityId = NO_ENTITY_ID;
        entityUuid = null;
        profileName = null;
        viewers.clear();
        nametagIds = new int[0];
        hologramIds = new int[0];
        lastSentYaw.clear();
        // Overrides deliberately survive: "hide this NPC from that player" is a decision another
        // plugin made, and a despawn is not the engine's cue to forget it.
    }

    /**
     * Forgets everything known about a player who has left.
     *
     * @param player the player who quit
     */
    public void forget(Player player) {
        Objects.requireNonNull(player, "player");
        viewers.remove(player);
        lastSentYaw.remove(player.getUniqueId());
        // Overrides are keyed by uuid and kept, so that a per-player NPC is still hidden or shown
        // correctly when the player comes back.
    }

    /**
     * Returns the players an override has been set for.
     *
     * @return an immutable snapshot of the overridden player ids
     */
    public Collection<UUID> overriddenPlayers() {
        return List.copyOf(overrides.keySet());
    }
}
