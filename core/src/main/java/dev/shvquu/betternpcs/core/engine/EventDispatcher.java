package dev.shvquu.betternpcs.core.engine;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

/**
 * How the engine fires its public events.
 *
 * <p>Indirected for the same reason as {@link Scheduler}: {@code Bukkit.getPluginManager()} needs a
 * running server, and "does cancelling this event actually stop the NPC from spawning" is exactly
 * the kind of thing worth having a test for.
 *
 * @since 1.0.0
 */
public interface EventDispatcher {

    /**
     * Fires an event and returns it.
     *
     * @param event the event to fire
     * @param <E>   the event type
     * @return the same event, after listeners have seen it
     * @throws NullPointerException if {@code event} is {@code null}
     */
    <E extends Event> E fire(E event);

    /**
     * Fires a cancellable event and reports whether it survived.
     *
     * @param event the event to fire
     * @param <E>   the event type
     * @return {@code true} if no listener cancelled it
     * @throws NullPointerException if {@code event} is {@code null}
     */
    default <E extends Event & Cancellable> boolean fireAndCheck(E event) {
        return !fire(event).isCancelled();
    }
}
