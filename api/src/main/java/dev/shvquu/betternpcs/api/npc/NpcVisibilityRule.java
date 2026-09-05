package dev.shvquu.betternpcs.api.npc;

import org.bukkit.entity.Player;

/**
 * An extra condition on whether a player may see an NPC.
 *
 * <p>Registered through {@link NpcManager#registerVisibilityRule(NpcVisibilityRule)} and consulted
 * by the tracker <em>after</em> the built-in distance and permission checks in
 * {@link dev.shvquu.betternpcs.api.npc.property.NpcVisibility} have passed. Every registered rule
 * must agree before the NPC is shown; a single rule returning {@code false} hides it.
 *
 * <p>This is the hook for everything the core deliberately does not know about — regions, parties,
 * quest progress, instance ownership, vanish plugins.
 *
 * <h2>Performance</h2>
 *
 * <p>A rule is called for every candidate pairing of a nearby player and NPC, several times a
 * second. On a server with a thousand NPCs that is a lot of calls, so an implementation must be a
 * cheap in-memory lookup. A rule that queries a database, walks a region tree or allocates per call
 * will show up in the server's tick time; cache the answer and invalidate it on the event that
 * changes it, then call {@link Npc#refreshVisibility()} so the tracker re-evaluates.
 *
 * <p>Rules are called on the main server thread.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface NpcVisibilityRule {

    /**
     * Returns whether this rule permits the player to see the NPC.
     *
     * @param npc    the NPC being considered
     * @param player the player being considered, online at the time of the call
     * @return {@code true} to allow, {@code false} to hide the NPC from this player
     */
    boolean isVisible(Npc npc, Player player);
}
