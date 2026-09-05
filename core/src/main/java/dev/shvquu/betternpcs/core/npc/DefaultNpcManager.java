package dev.shvquu.betternpcs.core.npc;

import dev.shvquu.betternpcs.api.event.NpcCreateEvent;
import dev.shvquu.betternpcs.api.event.NpcDeleteEvent;
import dev.shvquu.betternpcs.api.event.NpcDespawnEvent;
import dev.shvquu.betternpcs.api.event.NpcLoadEvent;
import dev.shvquu.betternpcs.api.npc.Npc;
import dev.shvquu.betternpcs.api.npc.NpcManager;
import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import dev.shvquu.betternpcs.api.npc.NpcState;
import dev.shvquu.betternpcs.api.npc.NpcType;
import dev.shvquu.betternpcs.api.npc.NpcVisibilityRule;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * The engine's {@link NpcManager}.
 *
 * <p>Owns creation, deletion and the load and save cycles. Everything about an individual NPC lives
 * on its {@link NpcHandle}; this class only does what needs a view of the whole set.
 *
 * <p>Mutating methods must be called on the main server thread. Lookups are safe from anywhere.
 *
 * @since 1.0.0
 */
public final class DefaultNpcManager implements NpcManager {

    /**
     * The characters an NPC name may contain.
     *
     * <p>Restricted because a name is typed into commands, used as a tab completion, and turned into
     * a profile name. A name with a space in it would need quoting everywhere and would still be
     * ambiguous in {@code /npc create}.
     */
    private static final String NAME_PATTERN = "[A-Za-z0-9_-]{1,32}";

    private final EngineServices services;

    /**
     * Creates the manager.
     *
     * @param services the engine's collaborators
     * @throws NullPointerException if {@code services} is {@code null}
     */
    public DefaultNpcManager(EngineServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    // ---------------------------------------------------------------------------------------------
    // Creation
    // ---------------------------------------------------------------------------------------------

    @Override
    public Npc create(String name, NpcType type, NpcPosition position) {
        return create(name, type, position, null);
    }

    /**
     * Creates an NPC, recording who asked for it.
     *
     * @param name     the NPC's name
     * @param type     what the NPC is rendered as
     * @param position where the NPC stands
     * @param creator  who asked, or {@code null} when a plugin created it through the API
     * @return the new NPC
     * @throws NullPointerException     if {@code name}, {@code type} or {@code position} is {@code null}
     * @throws IllegalArgumentException if the name is unusable or already taken
     * @throws IllegalStateException    if a listener cancelled the creation
     */
    public Npc create(String name, NpcType type, NpcPosition position, CommandSender creator) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");

        String trimmed = name.trim();
        validateName(trimmed);
        if (services.registry().contains(trimmed)) {
            throw new IllegalArgumentException("An NPC called '" + trimmed + "' already exists");
        }

        NpcSnapshot snapshot = NpcSnapshot.builder(UUID.randomUUID(), trimmed, type, position).build();
        return register(snapshot, NpcState.CREATED, creator);
    }

    @Override
    public Npc create(NpcSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        validateName(snapshot.name());

        if (services.registry().contains(snapshot.name())) {
            throw new IllegalArgumentException("An NPC called '" + snapshot.name() + "' already exists");
        }
        if (services.registry().byUniqueId(snapshot.uniqueId()).isPresent()) {
            throw new IllegalArgumentException("An NPC with the id " + snapshot.uniqueId() + " already exists");
        }
        return register(snapshot, NpcState.CREATED, null);
    }

    private Npc register(NpcSnapshot snapshot, NpcState state, CommandSender creator) {
        NpcHandle npc = new NpcHandle(snapshot, services, state);

        // Fired before registration, so a listener can configure the NPC but cannot look it up yet.
        // That is deliberate: an NPC visible to lookups before its creation was approved would be a
        // half-created NPC other plugins could act on.
        if (!services.events().fireAndCheck(new NpcCreateEvent(npc, creator))) {
            throw new IllegalStateException(
                    "The creation of the NPC '" + snapshot.name() + "' was cancelled by another plugin");
        }

        services.registry().register(npc);
        return npc;
    }

    /**
     * Checks that a name can be used for an NPC.
     *
     * @param name the name to check
     * @throws IllegalArgumentException if it cannot
     */
    public static void validateName(String name) {
        if (name == null || !name.matches(NAME_PATTERN)) {
            throw new IllegalArgumentException(
                    "'" + name + "' is not a usable NPC name. Use 1 to 32 letters, digits, '_' or '-'.");
        }
    }

    /**
     * Returns whether a name can be used for an NPC.
     *
     * @param name the name to check
     * @return {@code true} if it is usable
     */
    public static boolean isValidName(String name) {
        return name != null && name.matches(NAME_PATTERN);
    }

    // ---------------------------------------------------------------------------------------------
    // Lookup
    // ---------------------------------------------------------------------------------------------

    @Override
    public Optional<Npc> byName(String name) {
        return services.registry().byName(name).map(Npc.class::cast);
    }

    @Override
    public Optional<Npc> byUniqueId(UUID uniqueId) {
        return services.registry().byUniqueId(uniqueId).map(Npc.class::cast);
    }

    @Override
    public boolean exists(String name) {
        return services.registry().contains(name);
    }

    @Override
    public Collection<Npc> all() {
        return List.copyOf(services.registry().all());
    }

    @Override
    public Collection<Npc> inWorld(String world) {
        Objects.requireNonNull(world, "world");
        return services.registry().all().stream()
                .filter(npc -> npc.position().world().equals(world))
                .map(Npc.class::cast)
                .toList();
    }

    @Override
    public Collection<Npc> near(Location location, double radius) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location.world");
        if (!(radius > 0)) {
            throw new IllegalArgumentException("The radius must be positive, was " + radius);
        }

        String world = location.getWorld().getName();
        double radiusSquared = radius * radius;

        List<NpcHandle> found = new ArrayList<>();
        for (NpcHandle npc : services.registry().all()) {
            if (npc.position().world().equals(world)
                    && npc.position().distanceSquaredTo(location) <= radiusSquared) {
                found.add(npc);
            }
        }
        found.sort(Comparator.comparingDouble(npc -> npc.position().distanceSquaredTo(location)));
        return List.copyOf(found);
    }

    @Override
    public Optional<Npc> targetedBy(Player player, double maxRange) {
        Objects.requireNonNull(player, "player");
        if (!(maxRange > 0)) {
            throw new IllegalArgumentException("The range must be positive, was " + maxRange);
        }

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();

        NpcHandle best = null;
        double bestDistance = Double.MAX_VALUE;

        // Packet NPCs have no hit box the server can ray-trace, so the ray is intersected against a
        // box derived from the NPC's position instead. Restricting the candidates to those within
        // range first keeps this cheap enough to run from a command.
        for (NpcHandle npc : services.registry().all()) {
            if (!npc.isSpawned() || !npc.position().world().equals(eye.getWorld().getName())) {
                continue;
            }
            double distanceSquared = npc.position().distanceSquaredTo(eye);
            if (distanceSquared > maxRange * maxRange || distanceSquared >= bestDistance) {
                continue;
            }

            Location feet = npc.position().toLocation(eye.getWorld());
            RayTraceResult hit = org.bukkit.util.BoundingBox
                    .of(feet.clone().add(-0.4, 0, -0.4), feet.clone().add(0.4, 2.0, 0.4))
                    .rayTrace(eye.toVector(), direction, maxRange);

            if (hit != null) {
                best = npc;
                bestDistance = distanceSquared;
            }
        }
        return Optional.ofNullable(best);
    }

    @Override
    public int count() {
        return services.registry().size();
    }

    // ---------------------------------------------------------------------------------------------
    // Deletion
    // ---------------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<Boolean> delete(Npc npc) {
        Objects.requireNonNull(npc, "npc");
        return delete(npc, null);
    }

    /**
     * Deletes an NPC, recording who asked.
     *
     * @param npc     the NPC to delete
     * @param remover who asked, or {@code null} when a plugin asked through the API
     * @return a future completing with {@code true} once the NPC has been deleted
     * @throws NullPointerException  if {@code npc} is {@code null}
     * @throws IllegalStateException if the NPC has already been removed
     */
    public CompletableFuture<Boolean> delete(Npc npc, CommandSender remover) {
        Objects.requireNonNull(npc, "npc");
        if (!(npc instanceof NpcHandle handle)) {
            throw new IllegalArgumentException("That NPC was not created by this manager");
        }
        if (handle.isRemoved()) {
            throw new IllegalStateException("The NPC '" + npc.name() + "' has already been removed");
        }

        if (!services.events().fireAndCheck(new NpcDeleteEvent(handle, remover))) {
            return CompletableFuture.completedFuture(false);
        }

        handle.despawn(NpcDespawnEvent.Reason.DELETED);
        services.registry().unregister(handle);
        handle.markRemoved();

        return services.repository().delete(handle.uniqueId())
                .exceptionally(failure -> {
                    // The NPC is already gone from memory, so refusing here would leave the two out
                    // of step in the direction that resurrects it on the next restart. Say so loudly
                    // instead.
                    services.logger().log(Level.SEVERE, failure, () ->
                            "The NPC '" + handle.name() + "' was removed in memory but could not be "
                                    + "deleted from storage. It will come back on the next restart.");
                    return false;
                });
    }

    @Override
    public CompletableFuture<Boolean> delete(String name) {
        Objects.requireNonNull(name, "name");
        return services.registry().byName(name)
                .map(npc -> delete(npc, null))
                .orElseGet(() -> CompletableFuture.completedFuture(false));
    }

    // ---------------------------------------------------------------------------------------------
    // Bulk lifecycle
    // ---------------------------------------------------------------------------------------------

    @Override
    public int spawnAll() {
        int spawned = 0;
        for (NpcHandle npc : services.registry().all()) {
            if (npc.isSpawned() || !npc.spawnByDefault()) {
                continue;
            }
            if (npc.position().toLocation().isEmpty()) {
                // The world is not loaded. Not an error: a world may be loaded later by a world
                // management plugin, and the world load listener spawns its NPCs then.
                continue;
            }
            if (npc.spawn(true)) {
                spawned++;
            }
        }
        return spawned;
    }

    @Override
    public int despawnAll() {
        int despawned = 0;
        for (NpcHandle npc : services.registry().all()) {
            if (npc.despawn(NpcDespawnEvent.Reason.SHUTDOWN)) {
                despawned++;
            }
        }
        return despawned;
    }

    /**
     * Spawns every NPC in one world that is configured to spawn.
     *
     * @param world the world name
     * @return the number spawned
     * @throws NullPointerException if {@code world} is {@code null}
     */
    public int spawnInWorld(String world) {
        Objects.requireNonNull(world, "world");
        int spawned = 0;
        for (NpcHandle npc : services.registry().all()) {
            if (!npc.isSpawned() && npc.spawnByDefault() && npc.position().world().equals(world)
                    && npc.spawn(true)) {
                spawned++;
            }
        }
        return spawned;
    }

    /**
     * Despawns every NPC in one world.
     *
     * @param world  the world name
     * @param reason why they are being despawned
     * @return the number despawned
     * @throws NullPointerException if either argument is {@code null}
     */
    public int despawnInWorld(String world, NpcDespawnEvent.Reason reason) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(reason, "reason");
        int despawned = 0;
        for (NpcHandle npc : services.registry().all()) {
            if (npc.position().world().equals(world) && npc.despawn(reason)) {
                despawned++;
            }
        }
        return despawned;
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<Integer> saveAll() {
        List<NpcHandle> dirty = new ArrayList<>();
        List<NpcSnapshot> snapshots = new ArrayList<>();

        for (NpcHandle npc : services.registry().all()) {
            if (!npc.isDirty()) {
                continue;
            }
            dev.shvquu.betternpcs.api.event.NpcSaveEvent event =
                    services.events().fire(
                            new dev.shvquu.betternpcs.api.event.NpcSaveEvent(npc, npc.snapshot()));
            if (event.isCancelled()) {
                continue;
            }
            dirty.add(npc);
            snapshots.add(event.getSnapshot());
        }

        if (snapshots.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        // Cleared before the write rather than after: a change made while the write is in flight
        // must leave the NPC dirty again, and clearing afterwards would discard it.
        dirty.forEach(NpcHandle::markClean);

        return services.repository().saveAll(snapshots)
                .thenApply(ignored -> snapshots.size())
                .exceptionally(failure -> {
                    dirty.forEach(NpcHandle::markDirty);
                    services.logger().log(Level.SEVERE, failure,
                            () -> "Could not save " + snapshots.size() + " NPCs. They stay marked as "
                                    + "unsaved and the next save cycle will try again.");
                    return 0;
                });
    }

    @Override
    public CompletableFuture<Integer> reload() {
        return saveAll().thenCompose(ignored -> services.repository().loadAll())
                .thenApply(snapshots -> {
                    // Back on the main thread for everything that touches engine state. The load
                    // itself ran on the repository's executor.
                    CompletableFuture<Integer> applied = new CompletableFuture<>();
                    services.scheduler().runOnMainThread(() -> {
                        try {
                            applied.complete(replaceAll(snapshots, true));
                        } catch (RuntimeException failure) {
                            applied.completeExceptionally(failure);
                        }
                    });
                    return applied;
                })
                .thenCompose(future -> future);
    }

    /**
     * Loads every NPC from storage into an empty registry.
     *
     * <p>Called once during enable. Separate from {@link #reload()} because startup has nothing to
     * save first and nothing to tear down.
     *
     * @return a future completing with the number loaded
     */
    public CompletableFuture<Integer> loadAll() {
        return services.repository().loadAll().thenCompose(snapshots -> {
            CompletableFuture<Integer> applied = new CompletableFuture<>();
            services.scheduler().runOnMainThread(() -> {
                try {
                    applied.complete(replaceAll(snapshots, false));
                } catch (RuntimeException failure) {
                    applied.completeExceptionally(failure);
                }
            });
            return applied;
        });
    }

    private int replaceAll(Collection<NpcSnapshot> snapshots, boolean reload) {
        for (NpcHandle existing : services.registry().all()) {
            existing.despawn(NpcDespawnEvent.Reason.SHUTDOWN);
            existing.markRemoved();
        }
        services.registry().clear();
        services.index().clear();
        services.tracker().reset();

        int loaded = 0;
        for (NpcSnapshot snapshot : snapshots) {
            try {
                NpcHandle npc = new NpcHandle(snapshot, services, NpcState.DESPAWNED);
                services.registry().register(npc);
                services.events().fire(new NpcLoadEvent(npc, reload));
                loaded++;
            } catch (RuntimeException broken) {
                // One unloadable NPC — a duplicate name from a hand-edited database, say — must not
                // take the other thousand down with it.
                services.logger().log(Level.WARNING, broken,
                        () -> "Skipping the stored NPC '" + snapshot.name() + "': it could not be loaded.");
            }
        }
        return loaded;
    }

    // ---------------------------------------------------------------------------------------------
    // Visibility rules
    // ---------------------------------------------------------------------------------------------

    @Override
    public void registerVisibilityRule(NpcVisibilityRule rule) {
        services.visibility().register(rule);
    }

    @Override
    public boolean unregisterVisibilityRule(NpcVisibilityRule rule) {
        return services.visibility().unregister(rule);
    }

    /**
     * Looks an NPC up by name and returns the engine's own handle.
     *
     * <p>Same lookup as {@link #byName(String)}, typed for callers inside the plugin that need the
     * engine-internal methods the public {@link Npc} interface deliberately does not expose.
     *
     * @param name the NPC's name, matched case insensitively
     * @return the handle, or empty if no NPC has that name
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public Optional<NpcHandle> handleByName(String name) {
        return services.registry().byName(name);
    }

    /**
     * Returns every NPC as the engine's own handle.
     *
     * <p>Same set as {@link #all()}, typed for callers inside the plugin.
     *
     * @return an immutable snapshot
     */
    public Collection<NpcHandle> handles() {
        return services.registry().all();
    }

    /**
     * Returns the names of every NPC, sorted, for tab completion.
     *
     * @param prefix the text typed so far, matched case insensitively
     * @return the matching names
     * @throws NullPointerException if {@code prefix} is {@code null}
     */
    public List<String> completeNames(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        String lower = prefix.toLowerCase(Locale.ROOT);
        return services.registry().all().stream()
                .map(NpcHandle::name)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .toList();
    }
}
