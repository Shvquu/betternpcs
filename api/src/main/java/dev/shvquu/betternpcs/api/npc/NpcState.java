package dev.shvquu.betternpcs.api.npc;

/**
 * Where an NPC is in its lifecycle.
 *
 * <p>The states form a line, not a graph:
 *
 * <pre>{@code
 *   CREATED ──▶ SPAWNED ◀──▶ DESPAWNED ──▶ REMOVED
 * }</pre>
 *
 * <p>An NPC loaded from storage starts at {@link #DESPAWNED} rather than {@link #CREATED}: the
 * distinction between the two is whether the NPC has ever been persisted, which is what decides
 * whether removing it needs a database write.
 *
 * <p>{@link #SPAWNED} means the NPC is active and being tracked, not that any particular player can
 * see it. Whether an individual player is shown the NPC is a separate question answered by
 * {@link Npc#isVisibleTo(org.bukkit.entity.Player)}.
 *
 * @since 1.0.0
 */
public enum NpcState {

    /**
     * Created in memory and not yet persisted or rendered.
     *
     * <p>An NPC is in this state only between {@link NpcManager#create} returning and its first save.
     */
    CREATED,

    /**
     * Persisted and known to the manager, but not rendered to anyone.
     *
     * <p>Also the state of every NPC immediately after startup, before its world has loaded.
     */
    DESPAWNED,

    /**
     * Active: tracked every tick and rendered to the players who can see it.
     */
    SPAWNED,

    /**
     * Deleted. A terminal state — the handle is dead and every mutating method on it throws.
     *
     * <p>Extensions holding a reference to an NPC should check {@link Npc#isRemoved()} rather than
     * assume a handle stays valid, because another plugin or an administrator may delete it at any
     * time.
     */
    REMOVED;

    /**
     * Returns whether the NPC is currently rendered and tracked.
     *
     * @return {@code true} for {@link #SPAWNED}
     */
    public boolean isActive() {
        return this == SPAWNED;
    }

    /**
     * Returns whether the NPC still exists.
     *
     * @return {@code false} for {@link #REMOVED}
     */
    public boolean isAlive() {
        return this != REMOVED;
    }
}
