package dev.shvquu.betternpcs.plugin;

import dev.shvquu.betternpcs.core.engine.EventDispatcher;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

/**
 * The {@link EventDispatcher} backed by Bukkit's plugin manager.
 *
 * @since 1.0.0
 */
final class BukkitEventDispatcher implements EventDispatcher {

    private final Logger logger;

    /**
     * Creates the dispatcher.
     *
     * @param logger where listener failures are reported
     * @throws NullPointerException if {@code logger} is {@code null}
     */
    BukkitEventDispatcher(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public <E extends Event> E fire(E event) {
        Objects.requireNonNull(event, "event");
        try {
            Bukkit.getPluginManager().callEvent(event);
        } catch (RuntimeException listenerFailure) {
            // Bukkit already logs a listener that throws, but it does not stop the exception from
            // propagating out of callEvent. Letting it escape here would abort an NPC spawn because
            // some unrelated plugin's listener has a bug.
            logger.log(Level.WARNING, listenerFailure,
                    () -> "A listener for " + event.getEventName() + " failed. Continuing.");
        }
        return event;
    }
}
