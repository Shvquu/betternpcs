package dev.shvquu.betternpcs.api.npc.property;

import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The client-visible state flags of an NPC — everything carried in entity metadata rather than in a
 * dedicated packet.
 *
 * <p>Grouping these into one immutable value keeps an NPC update to a single metadata packet: the
 * engine diffs the old appearance against the new one and sends only what changed, instead of one
 * packet per setter call. That is the difference between a few hundred packets and a few thousand
 * when a scripted NPC toggles several flags in the same tick.
 *
 * <p>Some flags are advisory on a packet-only NPC and are documented individually where that is the
 * case. Build instances with {@link #builder()}; {@link #DEFAULT} is a plain, visible, non-glowing
 * NPC.
 *
 * @since 1.0.0
 */
public final class NpcAppearance {

    /** A visible, upright, non-glowing NPC with gravity and collisions disabled. */
    public static final NpcAppearance DEFAULT = builder().build();

    private final boolean glowing;
    private final NamedTextColor glowColor;
    private final boolean sneaking;
    private final boolean onFire;
    private final boolean invisible;
    private final boolean collidable;
    private final boolean gravity;
    private final boolean silent;
    private final boolean invulnerable;
    private final boolean listedInTablist;

    private NpcAppearance(Builder builder) {
        this.glowing = builder.glowing;
        this.glowColor = builder.glowColor;
        this.sneaking = builder.sneaking;
        this.onFire = builder.onFire;
        this.invisible = builder.invisible;
        this.collidable = builder.collidable;
        this.gravity = builder.gravity;
        this.silent = builder.silent;
        this.invulnerable = builder.invulnerable;
        this.listedInTablist = builder.listedInTablist;
    }

    /**
     * Returns a builder pre-filled with the default appearance.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a builder pre-filled with this appearance, for deriving a modified copy.
     *
     * @return a new builder holding this appearance's values
     */
    public Builder toBuilder() {
        return new Builder()
                .glowing(glowing)
                .glowColor(glowColor)
                .sneaking(sneaking)
                .onFire(onFire)
                .invisible(invisible)
                .collidable(collidable)
                .gravity(gravity)
                .silent(silent)
                .invulnerable(invulnerable)
                .listedInTablist(listedInTablist);
    }

    /**
     * Returns whether the NPC has the glowing outline.
     *
     * @return {@code true} if glowing
     */
    public boolean glowing() {
        return glowing;
    }

    /**
     * Returns the colour of the glowing outline.
     *
     * <p>Colouring a glow requires the entity to be a member of a scoreboard team whose colour is
     * set, so the engine maintains one team per colour it is asked for. An empty value means the
     * client's default white outline, which needs no team at all.
     *
     * @return the glow colour, or empty for the default white outline
     */
    public Optional<NamedTextColor> glowColor() {
        return Optional.ofNullable(glowColor);
    }

    /**
     * Returns whether the NPC is crouching.
     *
     * @return {@code true} if sneaking
     */
    public boolean sneaking() {
        return sneaking;
    }

    /**
     * Returns whether the NPC renders as burning.
     *
     * @return {@code true} if on fire
     */
    public boolean onFire() {
        return onFire;
    }

    /**
     * Returns whether the NPC's model is hidden.
     *
     * <p>An invisible NPC still receives interactions, which is how a bare hologram with a clickable
     * hit box is built.
     *
     * @return {@code true} if invisible
     */
    public boolean invisible() {
        return invisible;
    }

    /**
     * Returns whether players collide with the NPC.
     *
     * <p>Collision is a client-side decision driven by scoreboard team membership, so enabling it
     * puts the NPC into a team with {@code pushOtherTeams} behaviour. Disabled by default: an NPC
     * that can be shoved out of position is the single most common complaint about NPC plugins.
     *
     * @return {@code true} if players collide with the NPC
     */
    public boolean collidable() {
        return collidable;
    }

    /**
     * Returns whether the NPC is affected by gravity.
     *
     * <p>Only meaningful for NPCs backed by a real server entity. Packet NPCs never move on their
     * own, so for them this flag is recorded and ignored.
     *
     * @return {@code true} if gravity applies
     */
    public boolean gravity() {
        return gravity;
    }

    /**
     * Returns whether the NPC suppresses its ambient and hurt sounds.
     *
     * @return {@code true} if silent
     */
    public boolean silent() {
        return silent;
    }

    /**
     * Returns whether the NPC ignores damage.
     *
     * <p>A packet NPC cannot be damaged in the first place; this flag decides whether an attack is
     * reported as a {@link dev.shvquu.betternpcs.api.event.NpcDamageEvent} or only as an
     * interaction.
     *
     * @return {@code true} if invulnerable
     */
    public boolean invulnerable() {
        return invulnerable;
    }

    /**
     * Returns whether a player NPC appears in the tab list.
     *
     * <p>Player NPCs must be announced to the client through a player info update before their spawn
     * packet, or the client renders nothing. Leaving them <em>listed</em> afterwards is a separate
     * choice, and is off by default because a hundred NPCs in the tab list is rarely wanted.
     *
     * <p>Ignored by non-player NPCs.
     *
     * @return {@code true} if the NPC stays listed in the tab list
     */
    public boolean listedInTablist() {
        return listedInTablist;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof NpcAppearance appearance
                && glowing == appearance.glowing
                && Objects.equals(glowColor, appearance.glowColor)
                && sneaking == appearance.sneaking
                && onFire == appearance.onFire
                && invisible == appearance.invisible
                && collidable == appearance.collidable
                && gravity == appearance.gravity
                && silent == appearance.silent
                && invulnerable == appearance.invulnerable
                && listedInTablist == appearance.listedInTablist;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                glowing, glowColor, sneaking, onFire, invisible,
                collidable, gravity, silent, invulnerable, listedInTablist);
    }

    /**
     * Returns a description listing only the flags that differ from the defaults.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        StringBuilder description = new StringBuilder("NpcAppearance[");
        int lengthBeforeFlags = description.length();
        appendIf(description, glowing, "glowing" + (glowColor == null ? "" : ":" + glowColor));
        appendIf(description, sneaking, "sneaking");
        appendIf(description, onFire, "onFire");
        appendIf(description, invisible, "invisible");
        appendIf(description, collidable, "collidable");
        appendIf(description, gravity, "gravity");
        appendIf(description, silent, "silent");
        appendIf(description, invulnerable, "invulnerable");
        appendIf(description, listedInTablist, "listed");
        if (description.length() == lengthBeforeFlags) {
            description.append("default");
        }
        return description.append(']').toString();
    }

    private static void appendIf(StringBuilder target, boolean condition, String flag) {
        if (condition) {
            if (target.charAt(target.length() - 1) != '[') {
                target.append(", ");
            }
            target.append(flag);
        }
    }

    /**
     * Builds {@link NpcAppearance} instances.
     *
     * <p>Not thread safe; build on one thread and share the immutable result.
     *
     * @since 1.0.0
     */
    public static final class Builder {

        private boolean glowing;
        private NamedTextColor glowColor;
        private boolean sneaking;
        private boolean onFire;
        private boolean invisible;
        private boolean collidable;
        private boolean gravity = true;
        private boolean silent = true;
        private boolean invulnerable = true;
        private boolean listedInTablist;

        private Builder() {
        }

        /**
         * Sets the glowing outline.
         *
         * @param value {@code true} to glow
         * @return this builder
         */
        public Builder glowing(boolean value) {
            this.glowing = value;
            return this;
        }

        /**
         * Sets the glow colour.
         *
         * @param value the colour, or {@code null} for the client's default white
         * @return this builder
         */
        public Builder glowColor(NamedTextColor value) {
            this.glowColor = value;
            return this;
        }

        /**
         * Sets the crouching pose.
         *
         * @param value {@code true} to crouch
         * @return this builder
         */
        public Builder sneaking(boolean value) {
            this.sneaking = value;
            return this;
        }

        /**
         * Sets the burning overlay.
         *
         * @param value {@code true} to render as burning
         * @return this builder
         */
        public Builder onFire(boolean value) {
            this.onFire = value;
            return this;
        }

        /**
         * Sets model invisibility.
         *
         * @param value {@code true} to hide the model
         * @return this builder
         */
        public Builder invisible(boolean value) {
            this.invisible = value;
            return this;
        }

        /**
         * Sets whether players collide with the NPC.
         *
         * @param value {@code true} to enable collisions
         * @return this builder
         */
        public Builder collidable(boolean value) {
            this.collidable = value;
            return this;
        }

        /**
         * Sets whether gravity applies.
         *
         * @param value {@code true} to enable gravity
         * @return this builder
         */
        public Builder gravity(boolean value) {
            this.gravity = value;
            return this;
        }

        /**
         * Sets sound suppression.
         *
         * @param value {@code true} to silence the NPC
         * @return this builder
         */
        public Builder silent(boolean value) {
            this.silent = value;
            return this;
        }

        /**
         * Sets damage immunity.
         *
         * @param value {@code true} to ignore damage
         * @return this builder
         */
        public Builder invulnerable(boolean value) {
            this.invulnerable = value;
            return this;
        }

        /**
         * Sets whether a player NPC stays listed in the tab list.
         *
         * @param value {@code true} to keep the NPC listed
         * @return this builder
         */
        public Builder listedInTablist(boolean value) {
            this.listedInTablist = value;
            return this;
        }

        /**
         * Builds the immutable appearance.
         *
         * @return the appearance
         */
        public NpcAppearance build() {
            return new NpcAppearance(this);
        }
    }
}
