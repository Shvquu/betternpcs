package dev.shvquu.betternpcs.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.event.Event;

/**
 * An {@link EventDispatcher} that records events and lets a test act as a listener.
 *
 * <p>Avoids standing up a real plugin manager for what is, from the engine's point of view, a single
 * question: was the event fired, and did anything cancel it.
 */
public final class RecordingEventDispatcher implements EventDispatcher {

    private final List<Event> fired = new ArrayList<>();
    private final List<Consumer<Event>> listeners = new ArrayList<>();

    @Override
    public <E extends Event> E fire(E event) {
        Objects.requireNonNull(event, "event");
        fired.add(event);
        listeners.forEach(listener -> listener.accept(event));
        return event;
    }

    /**
     * Registers something to run for every event, which may cancel it.
     *
     * @param listener what to run
     */
    public void listen(Consumer<Event> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Returns every event fired, in order.
     *
     * @return the events
     */
    public List<Event> fired() {
        return List.copyOf(fired);
    }

    /**
     * Returns the fired events of one type.
     *
     * @param type the event class
     * @param <E>  the event type
     * @return the matching events, in order
     */
    public <E extends Event> List<E> firedOfType(Class<E> type) {
        return fired.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /**
     * Returns how many events of a type were fired.
     *
     * @param type the event class
     * @return the count
     */
    public int count(Class<? extends Event> type) {
        return (int) fired.stream().filter(type::isInstance).count();
    }

    /**
     * Forgets every recorded event.
     */
    public void clear() {
        fired.clear();
    }
}
