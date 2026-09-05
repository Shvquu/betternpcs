package dev.shvquu.betternpcs.api.event;

import dev.shvquu.betternpcs.api.npc.Npc;
import java.util.Objects;
import org.bukkit.event.Event;

/**
 * Base class for every event BetterNPCs fires about a particular NPC.
 *
 * <p>All BetterNPCs events are synchronous and fired on the main server thread, including those
 * whose cause was asynchronous — a skin that finished resolving on a background thread is applied,
 * and any resulting event fired, back on the main thread. Listeners can therefore call the Bukkit
 * API freely.
 *
 * @since 1.0.0
 */
public abstract class NpcEvent extends Event {

    private final Npc npc;

    /**
     * Creates the event.
     *
     * @param npc the NPC the event is about
     * @throws NullPointerException if {@code npc} is {@code null}
     */
    protected NpcEvent(Npc npc) {
        super(false);
        this.npc = Objects.requireNonNull(npc, "npc");
    }

    /**
     * Returns the NPC this event is about.
     *
     * @return the NPC
     */
    public Npc getNpc() {
        return npc;
    }
}
