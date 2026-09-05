package dev.shvquu.betternpcs.api.npc.property;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The floating text lines rendered above an NPC, in addition to its nametag.
 *
 * <p>Lines are stored as MiniMessage source rather than as rendered
 * {@link net.kyori.adventure.text.Component}s. That is not a shortcut: placeholders resolve per
 * viewing player, so a pre-rendered component could not show one player their own name and another
 * player theirs. Rendering happens at send time, once per viewer, from this source.
 *
 * <p>Lines are ordered top to bottom, matching how they read in a configuration file.
 *
 * <p>Instances are immutable and safe to share across threads.
 *
 * @since 1.0.0
 */
public final class HologramSettings {

    /**
     * Passed as the view distance to mean "the same distance the NPC itself is visible from".
     */
    public static final double INHERIT_VIEW_DISTANCE = -1.0;

    /** No hologram at all. */
    public static final HologramSettings NONE = builder().build();

    private final List<String> lines;
    private final double verticalOffset;
    private final double viewDistance;
    private final TextDisplayStyle style;

    private HologramSettings(Builder builder) {
        this.lines = List.copyOf(builder.lines);
        this.verticalOffset = builder.verticalOffset;
        this.viewDistance = builder.viewDistance;
        this.style = builder.style;
    }

    /**
     * Returns a builder for an empty hologram.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a hologram with the given lines and the default style.
     *
     * @param lines the MiniMessage lines, top to bottom
     * @return the hologram settings
     * @throws NullPointerException if {@code lines} or any element is {@code null}
     */
    public static HologramSettings of(String... lines) {
        return builder().lines(Arrays.asList(lines)).build();
    }

    /**
     * Returns a builder pre-filled with these settings, for deriving a modified copy.
     *
     * @return a new builder holding these values
     */
    public Builder toBuilder() {
        return new Builder()
                .lines(lines)
                .verticalOffset(verticalOffset)
                .viewDistance(viewDistance)
                .style(style);
    }

    /**
     * Returns the MiniMessage source of each line, ordered top to bottom.
     *
     * @return an immutable list, empty if the NPC has no hologram
     */
    public List<String> lines() {
        return lines;
    }

    /**
     * Returns whether any line is configured.
     *
     * @return {@code true} if there is nothing to render
     */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * Returns how far above the NPC's nametag the lowest hologram line sits, in blocks.
     *
     * @return the vertical offset in blocks
     */
    public double verticalOffset() {
        return verticalOffset;
    }

    /**
     * Returns the radius in blocks within which the hologram is rendered.
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
     * Returns how the lines look.
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
        return other instanceof HologramSettings settings
                && lines.equals(settings.lines)
                && Double.compare(verticalOffset, settings.verticalOffset) == 0
                && Double.compare(viewDistance, settings.viewDistance) == 0
                && style.equals(settings.style);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lines, verticalOffset, viewDistance, style);
    }

    /**
     * Returns a short description of these settings.
     *
     * @return the description
     */
    @Override
    public String toString() {
        return "HologramSettings[" + lines.size() + " lines, offset=" + verticalOffset
                + ", distance=" + (inheritsViewDistance() ? "inherit" : viewDistance) + ']';
    }

    /**
     * Builds {@link HologramSettings} instances.
     *
     * <p>Not thread safe; build on one thread and share the immutable result.
     *
     * @since 1.0.0
     */
    public static final class Builder {

        private final List<String> lines = new ArrayList<>();
        private double verticalOffset = 0.0;
        private double viewDistance = INHERIT_VIEW_DISTANCE;
        private TextDisplayStyle style = TextDisplayStyle.DEFAULT;

        private Builder() {
        }

        /**
         * Replaces every line.
         *
         * @param values the MiniMessage lines, top to bottom
         * @return this builder
         * @throws NullPointerException if {@code values} or any element is {@code null}
         */
        public Builder lines(Collection<String> values) {
            Objects.requireNonNull(values, "lines");
            lines.clear();
            values.forEach(this::line);
            return this;
        }

        /**
         * Appends one line below the existing ones.
         *
         * @param value the MiniMessage source of the line
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder line(String value) {
            lines.add(Objects.requireNonNull(value, "line"));
            return this;
        }

        /**
         * Sets how far above the nametag the lowest line sits.
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
         * Sets the radius within which the hologram renders.
         *
         * @param value the radius in blocks, or {@link #INHERIT_VIEW_DISTANCE}
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is zero, or negative and not
         *                                  {@link #INHERIT_VIEW_DISTANCE}
         */
        public Builder viewDistance(double value) {
            if (value != INHERIT_VIEW_DISTANCE && !(value > 0.0)) {
                throw new IllegalArgumentException(
                        "Hologram view distance must be positive or INHERIT_VIEW_DISTANCE, was " + value);
            }
            this.viewDistance = value;
            return this;
        }

        /**
         * Sets how the lines look.
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
         * @return the hologram settings
         */
        public HologramSettings build() {
            return new HologramSettings(this);
        }
    }
}
