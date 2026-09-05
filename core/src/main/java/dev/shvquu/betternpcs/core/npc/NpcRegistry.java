package dev.shvquu.betternpcs.core.npc;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The in-memory index of every NPC, by unique id, by name and by entity id.
 *
 * <p>Three indexes rather than one list because all three are hot in different places. Name lookup
 * runs on every command and tab completion. Unique id lookup runs on every load and save. Entity id
 * lookup runs on <em>every interaction packet from every player</em>, including the ones aimed at
 * real entities — a linear scan there would put the cost of the whole plugin on the network thread.
 *
 * <p>Thread safe for reads, which matters because entity id lookup happens on the network thread
 * while the main thread may be registering an NPC. Writes are expected on the main thread.
 *
 * @since 1.0.0
 */
public final class NpcRegistry {

    private final Map<UUID, NpcHandle> byUniqueId = new ConcurrentHashMap<>();
    private final Map<String, NpcHandle> byName = new ConcurrentHashMap<>();
    private final Map<Integer, NpcHandle> byEntityId = new ConcurrentHashMap<>();

    /**
     * Adds an NPC to every index.
     *
     * @param npc the NPC to register
     * @throws NullPointerException     if {@code npc} is {@code null}
     * @throws IllegalArgumentException if its name or unique id is already registered
     */
    public void register(NpcHandle npc) {
        Objects.requireNonNull(npc, "npc");
        String key = key(npc.name());

        // Reserve the name first: it is the index a user-facing conflict is reported against, and
        // failing before the unique id map is touched keeps the registry consistent.
        NpcHandle nameClash = byName.putIfAbsent(key, npc);
        if (nameClash != null) {
            throw new IllegalArgumentException("An NPC called '" + npc.name() + "' already exists");
        }

        NpcHandle idClash = byUniqueId.putIfAbsent(npc.uniqueId(), npc);
        if (idClash != null) {
            byName.remove(key, npc);
            throw new IllegalArgumentException("An NPC with the id " + npc.uniqueId() + " already exists");
        }

        indexEntityIds(npc);
    }

    /**
     * Removes an NPC from every index.
     *
     * @param npc the NPC to remove
     * @return {@code true} if it was registered
     * @throws NullPointerException if {@code npc} is {@code null}
     */
    public boolean unregister(NpcHandle npc) {
        Objects.requireNonNull(npc, "npc");
        boolean removed = byUniqueId.remove(npc.uniqueId(), npc);
        byName.remove(key(npc.name()), npc);
        forgetEntityIds(npc);
        return removed;
    }

    /**
     * Moves an NPC to a new name.
     *
     * <p>Both index changes happen here rather than in the handle, so that the window in which a
     * lookup could find neither name does not exist.
     *
     * @param npc     the NPC being renamed
     * @param newName the new name
     * @throws NullPointerException     if either argument is {@code null}
     * @throws IllegalArgumentException if another NPC already has {@code newName}
     */
    public void rename(NpcHandle npc, String newName) {
        Objects.requireNonNull(npc, "npc");
        Objects.requireNonNull(newName, "newName");

        String newKey = key(newName);
        String oldKey = key(npc.name());
        if (newKey.equals(oldKey)) {
            // A change of capitalisation only. The index key does not move.
            return;
        }

        NpcHandle clash = byName.putIfAbsent(newKey, npc);
        if (clash != null) {
            throw new IllegalArgumentException("An NPC called '" + newName + "' already exists");
        }
        byName.remove(oldKey, npc);
    }

    /**
     * Re-indexes an NPC's entity ids after they have changed.
     *
     * <p>Entity ids are allocated when an NPC spawns and released when it despawns, so this is
     * called on both transitions.
     *
     * @param npc the NPC whose ids changed
     * @throws NullPointerException if {@code npc} is {@code null}
     */
    public void refreshEntityIds(NpcHandle npc) {
        Objects.requireNonNull(npc, "npc");
        forgetEntityIds(npc);
        indexEntityIds(npc);
    }

    private void indexEntityIds(NpcHandle npc) {
        int entityId = npc.renderState().entityId();
        if (entityId != NpcRenderState.NO_ENTITY_ID) {
            byEntityId.put(entityId, npc);
        }
    }

    private void forgetEntityIds(NpcHandle npc) {
        // Removed by value, not by key: an id released by this NPC may already have been handed to
        // another one, and removing it by key alone would unregister the wrong NPC.
        byEntityId.values().removeIf(candidate -> candidate == npc);
    }

    /**
     * Looks an NPC up by name, case insensitively.
     *
     * @param name the name
     * @return the NPC, or empty if no NPC has that name
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public Optional<NpcHandle> byName(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(byName.get(key(name)));
    }

    /**
     * Looks an NPC up by unique id.
     *
     * @param uniqueId the unique id
     * @return the NPC, or empty if no NPC has that id
     * @throws NullPointerException if {@code uniqueId} is {@code null}
     */
    public Optional<NpcHandle> byUniqueId(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return Optional.ofNullable(byUniqueId.get(uniqueId));
    }

    /**
     * Looks an NPC up by the entity id its clients know it as.
     *
     * <p>Called from the network thread for every interaction packet, including those aimed at real
     * entities, so this must stay a single map lookup.
     *
     * @param entityId the entity id from an interaction packet
     * @return the NPC, or empty if the id belongs to a real entity or to nothing
     */
    public Optional<NpcHandle> byEntityId(int entityId) {
        return Optional.ofNullable(byEntityId.get(entityId));
    }

    /**
     * Returns every registered NPC.
     *
     * @return an immutable snapshot
     */
    public Collection<NpcHandle> all() {
        return List.copyOf(byUniqueId.values());
    }

    /**
     * Returns how many NPCs are registered.
     *
     * @return the count
     */
    public int size() {
        return byUniqueId.size();
    }

    /**
     * Returns whether a name is taken.
     *
     * @param name the name to check
     * @return {@code true} if an NPC has that name
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public boolean contains(String name) {
        Objects.requireNonNull(name, "name");
        return byName.containsKey(key(name));
    }

    /**
     * Empties every index.
     *
     * <p>Used by reload, which replaces the whole registry rather than reconciling it.
     */
    public void clear() {
        byUniqueId.clear();
        byName.clear();
        byEntityId.clear();
    }

    private static String key(String name) {
        // Locale.ROOT, not the default: with a Turkish locale, "NPC".toLowerCase() produces a
        // dotless i and the name silently stops matching itself.
        return name.toLowerCase(Locale.ROOT);
    }
}
