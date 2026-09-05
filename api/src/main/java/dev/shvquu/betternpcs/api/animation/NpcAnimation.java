package dev.shvquu.betternpcs.api.animation;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A one-shot visual effect an NPC can play.
 *
 * <p>Minecraft has no single "animation" concept. What players think of as one is delivered through
 * three unrelated mechanisms: the animation packet for limb movement, the entity event packet for
 * reactions such as taking damage, and particle packets for everything else. This enum unifies all
 * three behind one name, and {@link #kind()} tells the version adapter which packet to build.
 *
 * <p>Poses are not animations and are not listed here. Crouching, for instance, is a state that
 * persists until it is cleared, so it lives on
 * {@link dev.shvquu.betternpcs.api.npc.property.NpcAppearance} instead.
 *
 * @since 1.0.0
 */
public enum NpcAnimation {

    /** Swings the main hand, as if hitting something. */
    SWING_MAIN_HAND(Kind.ANIMATION),

    /** Swings the off hand. */
    SWING_OFF_HAND(Kind.ANIMATION),

    /** Plays the hurt flash and sound. */
    TAKE_DAMAGE(Kind.ENTITY_EVENT),

    /** Shows the critical hit particles. */
    CRITICAL_HIT(Kind.ENTITY_EVENT),

    /** Shows the enchanted-weapon critical hit particles. */
    MAGIC_CRITICAL_HIT(Kind.ENTITY_EVENT),

    /**
     * Shows a villager's angry particles.
     *
     * <p>Only renders on NPCs the client treats as villagers; the adapter falls back to the
     * equivalent particle for other types.
     */
    VILLAGER_ANGRY(Kind.ENTITY_EVENT),

    /** Shows a villager's happy particles. */
    VILLAGER_HAPPY(Kind.ENTITY_EVENT),

    /** Shows the love-mode hearts. */
    HEART(Kind.PARTICLE),

    /** Shows a puff of flame around the NPC. */
    FLAME(Kind.PARTICLE),

    /** Shows a puff of smoke around the NPC. */
    SMOKE(Kind.PARTICLE),

    /** Plays the totem of undying effect. */
    TOTEM(Kind.ENTITY_EVENT);

    /**
     * Which protocol mechanism delivers an animation.
     *
     * <p>Read by the version adapters, which need to build a different packet for each.
     *
     * @since 1.0.0
     */
    public enum Kind {

        /** Sent as an entity animation packet. */
        ANIMATION,

        /** Sent as an entity event packet. */
        ENTITY_EVENT,

        /** Sent as particles positioned around the NPC. */
        PARTICLE
    }

    private final Kind kind;

    NpcAnimation(Kind kind) {
        this.kind = kind;
    }

    /**
     * Returns which protocol mechanism delivers this animation.
     *
     * @return the kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Parses an animation name, case insensitively.
     *
     * <p>Used by commands and configuration files, where an unknown name should produce a message
     * rather than an exception.
     *
     * @param name the animation name
     * @return the animation, or empty if no animation has that name
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public static Optional<NpcAnimation> byName(String name) {
        Objects.requireNonNull(name, "name");
        try {
            return Optional.of(valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
