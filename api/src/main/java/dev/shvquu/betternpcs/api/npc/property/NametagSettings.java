package dev.shvquu.betternpcs.api.npc.property;

import java.util.Objects;
import java.util.Optional;

/**
 * The name rendered above an NPC.
 *
 * <p>BetterNPCs draws nametags itself rather than using the entity's vanilla custom name. The
 * vanilla name is a single component sent once with the entity's metadata, which rules out
 * placeholders that differ per viewer, MiniMessage gradients on older clients, permission-gated
 * visibility and a per-nametag view distance — all of which this type supports.
 *
 * <p>{@link #text()} is MiniMessage source, resolved per viewer at send time. The token
 * {@code <npc_name>} expands to the NPC's display name, which is what makes a shared prefix and
 * suffix template such as {@code "<gray>[Shop] <white><npc_name>"} reusable across NPCs.
 *
 * <p>Instances are immutable and safe to share across threads.
 *
 * @since 1.0.0
 */
public final class NametagSettings {

    /**
     * Passed as the view distance to mean "the same distance the NPC itself is visible from".
     */
    public static final double INHERIT_VIEW_DISTANCE = -1.0;

    /**
     * The placeholder that expands to the NPC's display name inside {@link #text()}.
     */
    public static final String NAME_PLACEHOLDER = "<npc_name>";

    /** A visible nametag showing the NPC's display name, in the default style. */
    public static final NametagSettings DEFAULT = builder().build();

    /** No nametag at all. */
    public static final NametagSettings HIDDEN = builder().visible(false).build();

    private final boolean visible;
    private final String text;
    private final double verticalOffset;
    private final double viewDistance;
    private final String permission;
    private final TextDisplayStyle style;

    private NametagSettings(Builder builder) {
        this.visible = builder.visible;
        this.text = builder.text;
        this.verticalOffset = builder.verticalOffset;
        this.viewDistance = builder.viewDistance;
        this.permission = builder.permission;
        this.style = builder.style;
    }

    /**
     * Returns a builder pre-filled with the default nametag.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a visible nametag with the given template.
     *
     * @param text the MiniMessage template, which may contain {@value #NAME_PLACEHOLDER}
     * @return the nametag settings
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public static NametagSettings of(String text) {
        return builder().text(Objects.requireNonNull(text, "text")).build();
    }

    /**
     * Returns a builder pre-filled with these settings, for deriving a modified copy.
     *
     * @return a new builder holding these values
     */
    public Builder toBuilder() {
        return new Builder()
                .visible(visible)
                .text(text)
                .verticalOffset(verticalOffset)
                .viewDistance(viewDistance)
                .permission(permission)
                .style(style);
    }

    /**
     * Returns whether the nametag is rendered at all.
     *
     * @return {@code true} if a nametag is shown
     */
    public boolean visible() {
        return visible;
    }

    /**
     * Returns the MiniMessage template used to render the nametag.
     *
     * @return the template, or empty to render the NPC's display name unchanged
     */
    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    /**
     * Returns how far above the NPC's head the nametag sits, relative to its default position.
     *
     * @return the vertical offset in blocks, {@code 0.0} for the default position
     */
    public double verticalOffset() {
        return verticalOffset;
    }

    /**
     * Returns the radius in blocks within which the nametag is rendered.
     *
     * @return the radius, or {@link #INHERIT_VIEW_DISTANCE} to match the NPC's own view distance
     */
    public double viewDistance() {
        return viewDistance;
    }

    /**
     * Returns whether {@link #viewDistance()} follows the NPC's own visibility range.
     *
     * @return {@code true} if the NPC's view distance applies
     */
    public boolean inheritsViewDistance() {
        return viewDistance == INHERIT_VIEW_DISTANCE;
    }

    /**
     * Returns the permission node a player must hold to see the nametag.
     *
     * <p>Separate from the NPC's own visibility permission: a staff-only label above an NPC everyone
     * can see is a common request, and would otherwise need two NPCs.
     *
     * @return the node, or empty if no permission is required
     */
    public Optional<String> permission() {
        return Optional.ofNullable(permission);
    }

    /**
     * Returns how the nametag looks.
     *
     * @return the text style
     */
    public TextDisplayStyle style() {
        return style;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof NametagSettings settings
                && visible == settings.visible
                && Objects.equals(text, settings.text)
                && Double.compare(verticalOffset, settings.verticalOffset) == 0
                && Double.compare(viewDistance, settings.viewDistance) == 0
                && Objects.equals(permission, settings.permission)
                && style.equals(settings.style);
    }

    @Override
    public int hashCode() {
        return Objects.hash(visible, text, verticalOffset, viewDistance, permission, style);
    }

    /**
     * Returns a short description of these settings.
     *
     * @return the description
     */
    @Override
    public String toString() {
        if (!visible) {
            return "NametagSettings[hidden]";
        }
        return "NametagSettings[" + (text == null ? "<display name>" : text)
                + (permission == null ? "" : ", permission=" + permission)
                + ", distance=" + (inheritsViewDistance() ? "inherit" : viewDistance) + ']';
    }

    /**
     * Builds {@link NametagSettings} instances.
     *
     * <p>Not thread safe; build on one thread and share the immutable result.
     *
     * @since 1.0.0
     */
    public static final class Builder {

        private boolean visible = true;
        private String text;
        private double verticalOffset = 0.0;
        private double viewDistance = INHERIT_VIEW_DISTANCE;
        private String permission;
        private TextDisplayStyle style = TextDisplayStyle.DEFAULT;

        private Builder() {
        }

        /**
         * Sets whether a nametag is rendered.
         *
         * @param value {@code true} to show a nametag
         * @return this builder
         */
        public Builder visible(boolean value) {
            this.visible = value;
            return this;
        }

        /**
         * Sets the MiniMessage template.
         *
         * @param value the template, which may contain {@value #NAME_PLACEHOLDER}, or {@code null}
         *              to render the display name unchanged
         * @return this builder
         */
        public Builder text(String value) {
            this.text = value;
            return this;
        }

        /**
         * Sets how far above its default position the nametag sits.
         *
         * @param value the vertical offset in blocks
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is not finite
         */
        public Builder verticalOffset(double value) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Vertical offset must be finite, was " + value);
            }
            this.verticalOffset = value;
            return this;
        }

        /**
         * Sets the radius within which the nametag renders.
         *
         * @param value the radius in blocks, or {@link #INHERIT_VIEW_DISTANCE}
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is zero, or negative and not
         *                                  {@link #INHERIT_VIEW_DISTANCE}
         */
        public Builder viewDistance(double value) {
            if (value != INHERIT_VIEW_DISTANCE && !(value > 0.0)) {
                throw new IllegalArgumentException(
                        "Nametag view distance must be positive or INHERIT_VIEW_DISTANCE, was " + value);
            }
            this.viewDistance = value;
            return this;
        }

        /**
         * Sets the permission node required to see the nametag.
         *
         * @param value the node, or {@code null} or blank for none
         * @return this builder
         */
        public Builder permission(String value) {
            this.permission = value == null || value.isBlank() ? null : value.trim();
            return this;
        }

        /**
         * Sets how the nametag looks.
         *
         * @param value the text style
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder style(TextDisplayStyle value) {
            this.style = Objects.requireNonNull(value, "style");
            return this;
        }

        /**
         * Builds the immutable settings.
         *
         * @return the nametag settings
         */
        public NametagSettings build() {
            return new NametagSettings(this);
        }
    }
}
