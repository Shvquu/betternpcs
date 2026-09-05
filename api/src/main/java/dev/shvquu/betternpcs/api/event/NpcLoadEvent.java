package dev.shvquu.betternpcs.api.event;

import dev.shvquu.betternpcs.api.npc.Npc;
import org.bukkit.event.HandlerList;

/**
 * Fired for each NPC once it has been loaded from storage and registered, before it spawns.
 *
 * <p>Where an extension re-attaches whatever it derived from {@link Npc#metadata()} the last time
 * the server ran. Fired on the main thread even though the load itself happened on a background
 * one.
 *
 * <p>Also fired after {@link dev.shvquu.betternpcs.api.npc.NpcManager#reload()}, which invalidates
 * every handle an extension was holding — treat this event, not plugin enable, as the point at which
 * NPC references become valid.
 *
 * @since 1.0.0
 */
public class NpcLoadEvent extends NpcEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean reload;

    /**
     * Creates the event.
     *
     * @param npc    the NPC that was loaded
     * @param reload {@code true} if this is a reload rather than the initial startup load
     * @throws NullPointerException if {@code npc} is {@code null}
     */
    public NpcLoadEvent(Npc npc, boolean reload) {
        super(npc);
        this.reload = reload;
    }

    /**
     * Returns whether this load is part of a reload.
     *
     * @return {@code true} for a reload, {@code false} for the initial load at startup
     */
    public boolean isReload() {
        return reload;
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
