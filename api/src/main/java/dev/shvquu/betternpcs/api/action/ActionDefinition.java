package dev.shvquu.betternpcs.api.action;

import java.util.Locale;
import java.util.Objects;

/**
 * One configured step of an NPC's reaction to an interaction: a handler id and the argument to pass
 * it.
 *
 * <p>Actions are stored as a type and a string rather than as executable objects, which is what
 * makes them serialisable, editable from a command, and safe to load from a database without
 * deserialising anything. The handler registered under {@link #type()} decides what the argument
 * means — {@code message} treats it as MiniMessage, {@code command} as a command line, and an
 * extension's own handler as whatever it likes.
 *
 * <p>The configuration form is {@code "type: argument"}, which {@link #parse(String)} reads and
 * {@link #serialize()} writes:
 *
 * <pre>{@code
 * actions:
 *   RIGHT_CLICK:
 *     - "message: <green>Welcome!"
 *     - "sound: ENTITY_PLAYER_LEVELUP"
 *     - "command: spawn"
 * }</pre>
 *
 * @param type     the handler id, always lower case
 * @param argument the argument to pass the handler, never {@code null} but possibly empty
 * @since 1.0.0
 */
public record ActionDefinition(String type, String argument) {

    /**
     * Creates a definition.
     *
     * @param type     the handler id; normalised to lower case
     * @param argument the argument, trimmed
     * @throws NullPointerException     if either argument is {@code null}
     * @throws IllegalArgumentException if {@code type} is blank or contains a colon or whitespace
     */
    public ActionDefinition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(argument, "argument");
        type = type.trim().toLowerCase(Locale.ROOT);
        if (type.isEmpty()) {
            throw new IllegalArgumentException("Action type must not be blank");
        }
        for (int i = 0; i < type.length(); i++) {
            char character = type.charAt(i);
            if (character == ':' || Character.isWhitespace(character)) {
                throw new IllegalArgumentException(
                        "Action type must not contain ':' or whitespace, was '" + type + "'");
            }
        }
        argument = argument.trim();
    }

    /**
     * Creates a definition with no argument.
     *
     * @param type the handler id
     * @return the definition
     * @throws NullPointerException     if {@code type} is {@code null}
     * @throws IllegalArgumentException if {@code type} is blank or contains a colon or whitespace
     */
    public static ActionDefinition of(String type) {
        return new ActionDefinition(type, "");
    }

    /**
     * Parses the {@code "type: argument"} configuration form.
     *
     * <p>Only the first colon separates the two, so an argument may contain as many further colons
     * as it likes — which it will, because MiniMessage tags and namespaced keys both use them.
     *
     * @param line the line to parse
     * @return the definition
     * @throws NullPointerException     if {@code line} is {@code null}
     * @throws IllegalArgumentException if {@code line} has no colon, or the part before it is not a
     *                                  valid type
     */
    public static ActionDefinition parse(String line) {
        Objects.requireNonNull(line, "line");
        int separator = line.indexOf(':');
        if (separator < 0) {
            // Tolerating a bare type would make "message hello" silently become an argument-less
            // "message hello" action that no handler serves. Failing here points at the real line.
            throw new IllegalArgumentException(
                    "Action must be written as 'type: argument', was '" + line + "'");
        }
        return new ActionDefinition(line.substring(0, separator), line.substring(separator + 1));
    }

    /**
     * Returns the {@code "type: argument"} configuration form.
     *
     * @return the serialised line, round-tripping through {@link #parse(String)}
     */
    public String serialize() {
        return argument.isEmpty() ? type + ":" : type + ": " + argument;
    }

    /**
     * Returns {@link #serialize()}.
     *
     * @return the serialised line
     */
    @Override
    public String toString() {
        return serialize();
    }
}
