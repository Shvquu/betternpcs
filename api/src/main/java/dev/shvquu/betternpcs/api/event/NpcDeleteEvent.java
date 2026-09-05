package dev.shvquu.betternpcs.api.event;

import dev.shvquu.betternpcs.api.npc.Npc;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired before an NPC is deleted.
 *
 * <p>The NPC is still fully usable here, which is the last chance an extension has to read whatever
 * it stored in {@link Npc#metadata()} and clean up its own records.
 *
 * <p>Cancelling keeps the NPC. The deletion request completes with {@code false} rather than
 * throwing, because unlike creation there is a perfectly good NPC to carry on with.
 *
 * @since 1.0.0
 */
public class NpcDeleteEvent extends NpcEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CommandSender remover;
    private boolean cancelled;

    /**
     * Creates the event.
     *
     * @param npc     the NPC being deleted
     * @param remover who asked for it, or {@code null} if it was deleted through the API
     * @throws NullPointerException if {@code npc} is {@code null}
     */
    public NpcDeleteEvent(Npc npc, CommandSender remover) {
        super(npc);
        this.remover = remover;
    }

    /**
     * Returns who asked for the deletion.
     *
     * @return the remover, or {@code null} if a plugin deleted the NPC through the API
     */
    public CommandSender getRemover() {
        return remover;
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
