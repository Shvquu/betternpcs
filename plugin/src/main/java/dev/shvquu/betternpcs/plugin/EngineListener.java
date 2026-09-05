package dev.shvquu.betternpcs.plugin;

import dev.shvquu.betternpcs.api.event.NpcDespawnEvent;
import dev.shvquu.betternpcs.core.npc.DefaultNpcManager;
import dev.shvquu.betternpcs.core.npc.EngineServices;
import dev.shvquu.betternpcs.core.version.VersionAdapter;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Keeps the engine in step with players joining and leaving and worlds loading and unloading.
 *
 * <p>Four events, each doing exactly one thing the engine cannot observe on its own.
 *
 * @since 1.0.0
 */
final class EngineListener implements Listener {

    private final EngineServices services;
    private final DefaultNpcManager manager;
    private final VersionAdapter adapter;
    private final NpcInteractionHandler interactions;

    /**
     * Creates the listener.
     *
     * @param services     the engine's collaborators
     * @param manager      the NPC manager
     * @param adapter      the version adapter, told which connections to watch
     * @param interactions the interaction handler, whose cooldowns are pruned on quit
     * @throws NullPointerException if any argument is {@code null}
     */
    EngineListener(
            EngineServices services,
            DefaultNpcManager manager,
            VersionAdapter adapter,
            NpcInteractionHandler interactions) {
        this.services = Objects.requireNonNull(services, "services");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.interactions = Objects.requireNonNull(interactions, "interactions");
    }

    /**
     * Starts watching a joining player's connection for interaction packets.
     *
     * <p>At {@link EventPriority#MONITOR} so the connection is fully set up. The tracker shows the
     * player whatever is near them on its next pass; nothing is sent from here.
     *
     * @param event the join
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        adapter.trackPlayer(event.getPlayer());
    }

    /**
     * Forgets a player who has left.
     *
     * <p>Nothing is sent — their client is gone. This only drops the bookkeeping, which would
     * otherwise keep the {@link org.bukkit.entity.Player} object reachable for the lifetime of the
     * server and slowly leak one viewer entry per NPC they ever saw.
     *
     * @param event the quit
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        adapter.untrackPlayer(event.getPlayer());
        services.tracker().forget(event.getPlayer());
        interactions.forget(event.getPlayer());
    }

    /**
     * Spawns the NPCs belonging to a world that has just loaded.
     *
     * <p>NPCs whose world was not loaded at startup stay despawned until this fires, which is what
     * makes BetterNPCs work with world management plugins that load worlds on demand.
     *
     * @param event the world load
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        int spawned = manager.spawnInWorld(event.getWorld().getName());
        if (spawned > 0) {
            services.logger().info(() ->
                    "Spawned " + spawned + " NPC(s) in the world '" + event.getWorld().getName() + "'.");
        }
    }

    /**
     * Despawns the NPCs of a world that is being unloaded.
     *
     * <p>They stay registered and come back when the world does. Leaving them spawned would mean the
     * tracker computing distances against a world that no longer exists.
     *
     * @param event the world unload
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        manager.despawnInWorld(event.getWorld().getName(), NpcDespawnEvent.Reason.WORLD_UNLOADED);
    }
}
