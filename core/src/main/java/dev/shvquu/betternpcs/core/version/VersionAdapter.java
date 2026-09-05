package dev.shvquu.betternpcs.core.version;

import dev.shvquu.betternpcs.api.animation.NpcAnimation;
import dev.shvquu.betternpcs.core.render.AdapterContext;
import dev.shvquu.betternpcs.core.render.NpcView;
import dev.shvquu.betternpcs.core.render.TextView;
import java.util.Collection;
import org.bukkit.entity.Player;

/**
 * The single seam between version-independent engine code and Minecraft's internals.
 *
 * <p>Implementations live in {@code versions/v*} and are the only place in the project allowed to
 * import {@code net.minecraft.*} or {@code org.bukkit.craftbukkit.*}; an ArchUnit test enforces
 * that. Exactly one implementation is class-loaded per server, chosen by
 * {@link VersionAdapterResolver} from the running Minecraft version.
 *
 * <h2>Shape of this interface</h2>
 *
 * <p>Every rendering method takes a <em>collection</em> of viewers rather than one. That is not
 * convenience: it lets an adapter build a packet once and write it to fifty connections, which is
 * the difference between fifty object graphs per NPC per update and one. Where a value genuinely
 * differs per viewer — a rotation that faces each player, a hologram line with that player's name in
 * it — the engine calls the single-viewer overload instead, and only for those NPCs.
 *
 * <p>The engine never passes an NPC handle, only a frozen {@link NpcView}. Adapters hold no per-NPC
 * state; everything they need arrives as an argument. That is what keeps six implementations
 * possible to review against each other.
 *
 * <h2>Threading</h2>
 *
 * <p>Every method except {@link #allocateEntityId()} is called on the main server thread.
 * Interactions are reported back from the network thread — see
 * {@link dev.shvquu.betternpcs.core.render.NpcInteractionSink}.
 *
 * <h2>Implementing</h2>
 *
 * <p>Implementations must have a public no-argument constructor that touches no Minecraft state:
 * the resolver instantiates the adapter to ask {@link #describe()} before the server is necessarily
 * ready. Real setup belongs in {@link #enable(AdapterContext)}.
 *
 * @since 1.0.0
 */
public interface VersionAdapter {

    /**
     * Returns a short human-readable description of what this adapter binds to, used in the startup
     * banner and in diagnostics.
     *
     * <p>Safe to call on a freshly constructed adapter, before {@link #enable(AdapterContext)}.
     *
     * @return a description such as {@code "Paper 26.2 (Mojang-mapped)"}
     */
    String describe();

    /**
     * Prepares the adapter for use.
     *
     * <p>Called once, on the main thread, after the server is fully started. This is where a packet
     * listener is installed and any reflection is resolved, so that a failure surfaces here with the
     * plugin's own error handling around it rather than mid-tick.
     *
     * @param context the engine handles the adapter may use
     * @throws IllegalStateException if the adapter cannot bind to this server after all, which
     *                               disables the plugin cleanly with a clear message
     */
    void enable(AdapterContext context);

    /**
     * Releases everything {@link #enable(AdapterContext)} acquired.
     *
     * <p>Called on plugin disable, including after a failed enable, so implementations must tolerate
     * being called when setup never completed.
     */
    void disable();

    /**
     * Returns an entity id no server entity is using.
     *
     * <p>Safe to call from any thread. Ids must not collide with real entities: a client that is
     * told about two entities with the same id renders neither correctly, and the failure looks like
     * a rendering bug rather than an id clash.
     *
     * @return a fresh entity id
     */
    int allocateEntityId();

    /**
     * Returns whether this Minecraft version has text display entities.
     *
     * <p>When it does not, floating text falls back to invisible armour stands, which can show one
     * line of coloured text and nothing else — no scale, no background, no alignment. The engine
     * uses this to warn once about styles it cannot honour rather than silently rendering something
     * different from what was configured.
     *
     * @return {@code true} if text displays are available
     */
    boolean supportsTextDisplays();

    /**
     * Starts watching a player's connection for interaction packets.
     *
     * <p>Called when a player joins, and for every online player when the plugin enables mid-session.
     *
     * @param player the player to watch
     */
    void trackPlayer(Player player);

    /**
     * Stops watching a player's connection.
     *
     * <p>Called when a player quits and when the plugin disables. Must tolerate a player that was
     * never tracked.
     *
     * @param player the player to stop watching
     */
    void untrackPlayer(Player player);

    /**
     * Makes an NPC appear for the given viewers.
     *
     * <p>For a player NPC this includes announcing the profile before the spawn packet; a client
     * that receives the spawn first renders nothing at all.
     *
     * @param npc     what to render
     * @param viewers who to render it for
     */
    void spawnNpc(NpcView npc, Collection<? extends Player> viewers);

    /**
     * Removes a player NPC from the tab list while leaving the entity itself visible.
     *
     * <p>Called a tick after {@link #spawnNpc}, not immediately: the client needs the profile to
     * resolve the entity's skin, and removing the entry in the same tick races that lookup and
     * leaves the NPC wearing the default skin.
     *
     * <p>Does nothing for a non-player NPC, or for one whose
     * {@link dev.shvquu.betternpcs.api.npc.property.NpcAppearance#listedInTablist()} is set.
     *
     * @param npc     the NPC whose entry to remove
     * @param viewers who to remove it for
     */
    void hideFromTabList(NpcView npc, Collection<? extends Player> viewers);

    /**
     * Removes entities from the given viewers' clients.
     *
     * <p>Takes plain ids rather than views because it is also how an NPC's nametag and hologram
     * lines are removed, and because the NPC may already be gone by the time this runs.
     *
     * @param entityIds the ids to remove
     * @param viewers   who to remove them for
     */
    void removeEntities(int[] entityIds, Collection<? extends Player> viewers);

    /**
     * Sends the NPC's current state flags.
     *
     * @param npc     the NPC whose metadata changed
     * @param viewers who to send it to
     */
    void updateMetadata(NpcView npc, Collection<? extends Player> viewers);

    /**
     * Sends the NPC's current equipment.
     *
     * @param npc     the NPC whose equipment changed
     * @param viewers who to send it to
     */
    void updateEquipment(NpcView npc, Collection<? extends Player> viewers);

    /**
     * Moves the NPC to the position in its view.
     *
     * <p>Only ever called for a move within one world. A world change is a despawn and a fresh
     * spawn, because the client has no concept of an entity crossing dimensions.
     *
     * @param npc     the NPC that moved
     * @param viewers who to send it to
     */
    void teleport(NpcView npc, Collection<? extends Player> viewers);

    /**
     * Turns an entity in place.
     *
     * <p>Takes an id rather than a view because this is the one call the engine makes per viewer
     * rather than per NPC: an NPC that faces each player individually sends a different rotation to
     * each of them.
     *
     * @param entityId the entity to turn
     * @param yaw      the yaw in degrees
     * @param pitch    the pitch in degrees
     * @param headOnly {@code true} to turn only the head, leaving the body as it was
     * @param viewers  who to send it to
     */
    void rotate(int entityId, float yaw, float pitch, boolean headOnly, Collection<? extends Player> viewers);

    /**
     * Plays a one-shot effect on an NPC.
     *
     * @param npc       the NPC to animate
     * @param animation what to play
     * @param viewers   who should see it
     */
    void playAnimation(NpcView npc, NpcAnimation animation, Collection<? extends Player> viewers);

    /**
     * Makes a line of floating text appear for one viewer.
     *
     * <p>Per viewer rather than broadcast because the text is already rendered and two players may
     * legitimately be shown different text for the same line.
     *
     * @param text   the line to render
     * @param viewer who to render it for
     */
    void spawnText(TextView text, Player viewer);

    /**
     * Updates a line of floating text a viewer has already been sent.
     *
     * @param text   the line, with its new content or position
     * @param viewer who to update it for
     */
    void updateText(TextView text, Player viewer);
}
