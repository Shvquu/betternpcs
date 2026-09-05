package dev.shvquu.betternpcs.api.event;

import dev.shvquu.betternpcs.api.npc.Npc;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired just before a newly created NPC is registered.
 *
 * <p>The NPC exists and can be configured from a listener, which is how a plugin applies defaults —
 * a house nametag style, a standard view distance — to every NPC an administrator creates. It is not
 * yet in the registry, so it cannot be looked up by name from here.
 *
 * <p>Cancelling aborts the creation. {@link dev.shvquu.betternpcs.api.npc.NpcManager#create} then
 * throws {@link IllegalStateException}, and the command that triggered it reports the refusal.
 *
 * @since 1.0.0
 */
public class NpcCreateEvent extends NpcEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CommandSender creator;
    private boolean cancelled;

    /**
     * Creates the event.
     *
     * @param npc     the NPC being created
     * @param creator who asked for it, or {@code null} if it was created by the API
     * @throws NullPointerException if {@code npc} is {@code null}
     */
    public NpcCreateEvent(Npc npc, CommandSender creator) {
        super(npc);
        this.creator = creator;
    }

    /**
     * Returns who asked for the NPC.
     *
     * @return the creator, or {@code null} if a plugin created the NPC through the API rather than a
     *         player or the console running a command
     */
    public CommandSender getCreator() {
        return creator;
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
