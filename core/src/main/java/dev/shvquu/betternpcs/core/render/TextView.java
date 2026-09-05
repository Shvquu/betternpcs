package dev.shvquu.betternpcs.core.render;

import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.api.npc.property.TextDisplayStyle;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;

/**
 * One line of floating text belonging to an NPC — a nametag or a hologram line.
 *
 * <p>Each line is its own entity with its own id, which is what allows a nametag to be shown to one
 * player and hidden from another, and what allows a hologram to change one line without resending
 * the rest.
 *
 * <p>{@link #position()} is the final world position, with the line's vertical offset already
 * applied. The engine does that arithmetic so that six adapters cannot each get it slightly
 * differently.
 *
 * <p>{@link #text()} is already rendered for its viewer, placeholders and all, because two players
 * looking at the same hologram may legitimately see different text.
 *
 * @param entityId   the entity id the client will know this line by
 * @param entityUuid the entity uuid
 * @param position   where the line floats, offset already applied
 * @param text       the rendered text
 * @param style      how the line should look
 * @since 1.0.0
 */
public record TextView(
        int entityId,
        UUID entityUuid,
        NpcPosition position,
        Component text,
        TextDisplayStyle style) {

    /**
     * Creates the view.
     *
     * @param entityId   the entity id
     * @param entityUuid the entity uuid
     * @param position   the final position
     * @param text       the rendered text
     * @param style      the visual style
     * @throws NullPointerException if any argument is {@code null}
     */
    public TextView {
        Objects.requireNonNull(entityUuid, "entityUuid");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(style, "style");
    }
}
