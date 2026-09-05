package dev.shvquu.betternpcs.api.event;

import dev.shvquu.betternpcs.api.npc.Npc;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired before an NPC becomes active.
 *
 * <p>"Active" means tracked and eligible to be rendered, not shown to any particular player. There
 * is deliberately no per-player spawn event: the tracker decides visibility for every nearby player
 * several times a second, and firing a Bukkit event each time would cost more than the rendering
 * itself. Filter per player with a
 * {@link dev.shvquu.betternpcs.api.npc.NpcVisibilityRule} instead.
 *
 * <p>Cancelling leaves the NPC despawned. {@link Npc#spawn()} then returns {@code false}.
 *
 * @since 1.0.0
 */
public class NpcSpawnEvent extends NpcEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean automatic;
    private boolean cancelled;

    /**
     * Creates the event.
     *
     * @param npc       the NPC being spawned
     * @param automatic {@code true} if the engine is spawning the NPC because its world loaded,
     *                  {@code false} if something asked for it explicitly
     * @throws NullPointerException if {@code npc} is {@code null}
     */
    public NpcSpawnEvent(Npc npc, boolean automatic) {
        super(npc);
        this.automatic = automatic;
    }

    /**
     * Returns whether the engine initiated this spawn.
     *
     * <p>Lets a listener that wants to control spawning itself cancel the automatic case while
     * leaving explicit {@link Npc#spawn()} calls — including its own — alone.
     *
     * @return {@code true} for an automatic spawn at world load or startup
     */
    public boolean isAutomatic() {
        return automatic;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns the handler list, as Bukkit's event system requires.
     *
     * @return the handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
