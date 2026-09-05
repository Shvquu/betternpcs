package dev.shvquu.betternpcs.api.event;

import dev.shvquu.betternpcs.api.npc.Npc;
import java.util.Objects;
import org.bukkit.event.HandlerList;

/**
 * Fired after an NPC has stopped being active.
 *
 * <p>Not cancellable, and fired after the fact: a despawn happens for reasons that cannot be
 * refused, such as the NPC's world unloading or the server shutting down, so an event that could
 * veto it would be a promise the engine cannot keep.
 *
 * @since 1.0.0
 */
public class NpcDespawnEvent extends NpcEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    /**
     * Why an NPC was despawned.
     *
     * @since 1.0.0
     */
    public enum Reason {

        /** Something called {@link Npc#despawn()}. */
        REQUESTED,

        /** The NPC's world was unloaded. */
        WORLD_UNLOADED,

        /** The NPC is being deleted. */
        DELETED,

        /** The plugin is shutting down or reloading. */
        SHUTDOWN
    }

    private final Reason reason;

    /**
     * Creates the event.
     *
     * @param npc    the NPC that was despawned
     * @param reason why it was despawned
     * @throws NullPointerException if either argument is {@code null}
     */
    public NpcDespawnEvent(Npc npc, Reason reason) {
        super(npc);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Returns why the NPC was despawned.
     *
     * @return the reason
     */
    public Reason getReason() {
        return reason;
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
