package dev.shvquu.betternpcs.api.npc;

import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * The registry of every NPC on the server, and the entry point for creating and deleting them.
 *
 * <p>Obtained from {@link dev.shvquu.betternpcs.api.BetterNPCsApi#npcManager()}.
 *
 * <h2>Threading</h2>
 *
 * <p>Lookups are safe from any thread and return handles that are not. Everything that changes the
 * registry — creating, deleting, loading — must be called on the main server thread.
 *
 * @since 1.0.0
 */
public interface NpcManager {

    /**
     * Creates an NPC and registers it.
     *
     * <p>The NPC starts in {@link NpcState#CREATED} and is not rendered until {@link Npc#spawn()} is
     * called. It is written to storage with the next save cycle.
     *
     * <p>Fires {@link dev.shvquu.betternpcs.api.event.NpcCreateEvent} before anything is registered;
     * cancelling it makes this method throw, because there is no sensible NPC to return.
     *
     * @param name     the NPC's name, unique among NPCs
     * @param type     what the NPC is rendered as
     * @param position where the NPC stands
     * @return the new NPC
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or already taken
     * @throws IllegalStateException    if a listener cancelled the creation
     */
    Npc create(String name, NpcType type, NpcPosition position);

    /**
     * Creates an NPC at a Bukkit location.
     *
     * @param name     the NPC's name, unique among NPCs
     * @param type     what the NPC is rendered as
     * @param location where the NPC stands; must have a world
     * @return the new NPC
     * @throws NullPointerException     if any argument or the location's world is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or already taken
     * @throws IllegalStateException    if a listener cancelled the creation
     */
    default Npc create(String name, NpcType type, Location location) {
        return create(name, type, NpcPosition.of(location));
    }

    /**
     * Creates an NPC from a snapshot, keeping its unique id.
     *
     * <p>Used by migrations and by anything restoring a backup, where the id has to be preserved so
     * that external references to the NPC keep resolving.
     *
     * @param snapshot the snapshot to create from
     * @return the new NPC
     * @throws NullPointerException     if {@code snapshot} is {@code null}
     * @throws IllegalArgumentException if the snapshot's name or unique id is already taken
     * @throws IllegalStateException    if a listener cancelled the creation
     */
    Npc create(NpcSnapshot snapshot);

    /**
     * Looks an NPC up by name, case insensitively.
     *
     * @param name the NPC's name
     * @return the NPC, or empty if no NPC has that name
     * @throws NullPointerException if {@code name} is {@code null}
     */
    Optional<Npc> byName(String name);

    /**
     * Looks an NPC up by its unique id.
     *
     * @param uniqueId the NPC's unique id
     * @return the NPC, or empty if no NPC has that id
     * @throws NullPointerException if {@code uniqueId} is {@code null}
     */
    Optional<Npc> byUniqueId(UUID uniqueId);

    /**
     * Returns whether an NPC with the given name exists.
     *
     * @param name the name to check, matched case insensitively
     * @return {@code true} if the name is taken
     * @throws NullPointerException if {@code name} is {@code null}
     */
    boolean exists(String name);

    /**
     * Returns every registered NPC.
     *
     * @return an immutable snapshot of the registry
     */
    Collection<Npc> all();

    /**
     * Returns every NPC in a world.
     *
     * @param world the world name
     * @return an immutable snapshot
     * @throws NullPointerException if {@code world} is {@code null}
     */
    Collection<Npc> inWorld(String world);

    /**
     * Returns every NPC within a radius of a location, nearest first.
     *
     * @param location the centre; must have a world
     * @param radius   the radius in blocks
     * @return an immutable list, nearest first
     * @throws NullPointerException     if {@code location} or its world is {@code null}
     * @throws IllegalArgumentException if {@code radius} is not positive
     */
    Collection<Npc> near(Location location, double radius);

    /**
     * Returns the NPC a player is looking at, if any is within reach.
     *
     * <p>What {@code /npc} commands use when no name is given, so that an administrator can point at
     * an NPC instead of remembering what it is called.
     *
     * @param player   the player whose line of sight to trace
     * @param maxRange the furthest distance in blocks to consider
     * @return the NPC, or empty if the player is not looking at one
     * @throws NullPointerException     if {@code player} is {@code null}
     * @throws IllegalArgumentException if {@code maxRange} is not positive
     */
    Optional<Npc> targetedBy(Player player, double maxRange);

    /**
     * Returns how many NPCs are registered.
     *
     * @return the count
     */
    int count();

    /**
     * Deletes an NPC.
     *
     * <p>Despawns it, removes it from the registry, invalidates its handle and deletes it from
     * storage. Fires {@link dev.shvquu.betternpcs.api.event.NpcDeleteEvent} first, which may cancel
     * the deletion.
     *
     * @param npc the NPC to delete
     * @return a future completing with {@code true} once the NPC has been deleted from storage, or
     *         with {@code false} if a listener cancelled the deletion
     * @throws NullPointerException  if {@code npc} is {@code null}
     * @throws IllegalStateException if the NPC has already been removed
     */
    CompletableFuture<Boolean> delete(Npc npc);

    /**
     * Deletes an NPC by name.
     *
     * @param name the NPC's name, matched case insensitively
     * @return a future completing with {@code true} once the NPC has been deleted, or with
     *         {@code false} if no NPC has that name or a listener cancelled the deletion
     * @throws NullPointerException if {@code name} is {@code null}
     */
    CompletableFuture<Boolean> delete(String name);

    /**
     * Spawns every NPC that is configured to spawn and whose world is loaded.
     *
     * @return the number of NPCs that were spawned by this call
     */
    int spawnAll();

    /**
     * Despawns every spawned NPC, leaving them registered.
     *
     * @return the number of NPCs that were despawned by this call
     */
    int despawnAll();

    /**
     * Writes every NPC with unsaved changes to storage.
     *
     * @return a future completing with the number of NPCs written
     */
    CompletableFuture<Integer> saveAll();

    /**
     * Discards the in-memory registry and loads it again from storage.
     *
     * <p>Unsaved changes are written first, so nothing is lost. Every existing {@link Npc} handle is
     * invalidated — including ones extensions are holding — so extensions should look their NPCs up
     * again after {@link dev.shvquu.betternpcs.api.event.NpcLoadEvent}.
     *
     * @return a future completing with the number of NPCs loaded
     */
    CompletableFuture<Integer> reload();

    /**
     * Registers an extra visibility condition.
     *
     * @param rule the rule to add
     * @throws NullPointerException if {@code rule} is {@code null}
     */
    void registerVisibilityRule(NpcVisibilityRule rule);

    /**
     * Removes a previously registered visibility condition.
     *
     * @param rule the rule to remove, compared by identity
     * @return {@code true} if the rule was registered
     * @throws NullPointerException if {@code rule} is {@code null}
     */
    boolean unregisterVisibilityRule(NpcVisibilityRule rule);
}
