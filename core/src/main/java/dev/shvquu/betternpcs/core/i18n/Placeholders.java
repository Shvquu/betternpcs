package dev.shvquu.betternpcs.core.i18n;

import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Builds the placeholder resolvers passed to {@link MessageService}.
 *
 * <p>Every factory here except {@link #component(String, Component)} inserts its value as
 * <em>literal text</em>. That is a security property, not a formatting preference: a message such as
 * {@code "<green>Welcome, <player>"} is rendered with a value the server does not control, and a
 * player named something tag-shaped could otherwise inject a colour, a hover, or — worst — a
 * {@code <click:run_command>} into a line other players see.
 *
 * @since 1.0.0
 */
public final class Placeholders {

    private Placeholders() {
        throw new AssertionError("No instances");
    }

    /**
     * Inserts a value as literal text.
     *
     * @param name  the placeholder name, without angle brackets
     * @param value the value; anything tag-shaped in it stays literal
     * @return the resolver
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public static TagResolver text(String name, String value) {
        Objects.requireNonNull(name, "name");
        return Placeholder.unparsed(name, value == null ? "" : value);
    }

    /**
     * Inserts a number as literal text.
     *
     * @param name  the placeholder name
     * @param value the value
     * @return the resolver
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public static TagResolver number(String name, long value) {
        return text(name, Long.toString(value));
    }

    /**
     * Inserts a decimal as literal text, rounded to two places.
     *
     * <p>Rounding here rather than at every call site keeps coordinates in messages readable — a raw
     * double prints as {@code 100.00000000000001} often enough to matter.
     *
     * @param name  the placeholder name
     * @param value the value
     * @return the resolver
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public static TagResolver decimal(String name, double value) {
        // Locale.ROOT, not the JVM default: without it a server running with a German locale renders
        // coordinates as "100,50" while an English one renders "100.50", so the same command
        // produces different output depending on where the machine happens to be.
        return text(name, String.format(Locale.ROOT, "%.2f", value));
    }

    /**
     * Inserts an already-rendered component.
     *
     * <p>The one factory that does not neutralise its input, because the component has already been
     * parsed and cannot introduce new tags. Use it for values that are meant to carry formatting,
     * such as an NPC's display name; never for text that came from a player.
     *
     * @param name  the placeholder name
     * @param value the component to insert
     * @return the resolver
     * @throws NullPointerException if either argument is {@code null}
     */
    public static TagResolver component(String name, Component value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        return Placeholder.component(name, value);
    }

    /**
     * Inserts an enum constant by its lower-case name.
     *
     * @param name  the placeholder name
     * @param value the constant, may be {@code null}
     * @return the resolver
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public static TagResolver enumeration(String name, Enum<?> value) {
        return text(name, value == null ? "" : value.name().toLowerCase(Locale.ROOT));
    }
}
