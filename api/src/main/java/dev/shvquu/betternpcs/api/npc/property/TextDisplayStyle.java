package dev.shvquu.betternpcs.api.npc.property;

import java.util.Objects;
import java.util.Optional;

/**
 * The visual style shared by every piece of floating text an NPC renders — its nametag and its
 * hologram lines.
 *
 * <p>Extracted into its own type because the two differ only in what they say and where they sit,
 * not in how they look. A server that wants a house style sets one of these up and reuses it.
 *
 * <p>Several options only exist on servers new enough to have text display entities. Where they do
 * not, the version adapter falls back to armour stands and ignores what it cannot honour rather than
 * failing; {@link #requiresTextDisplays()} reports whether a style depends on that support.
 *
 * <p>Instances are immutable and safe to share across threads.
 *
 * @since 1.0.0
 */
public final class TextDisplayStyle {

    /**
     * Passed as the background colour to mean "the client's default translucent black".
     */
    public static final int DEFAULT_BACKGROUND = 0x40000000;

    /** Fully opaque text. */
    public static final int OPAQUE = 255;

    /** The default style: centred, billboarded towards the viewer, default background. */
    public static final TextDisplayStyle DEFAULT = builder().build();

    private final Billboard billboard;
    private final TextAlignment alignment;
    private final float scale;
    private final double lineHeight;
    private final Integer backgroundColor;
    private final int textOpacity;
    private final boolean seeThrough;
    private final boolean shadowed;

    private TextDisplayStyle(Builder builder) {
        this.billboard = builder.billboard;
        this.alignment = builder.alignment;
        this.scale = builder.scale;
        this.lineHeight = builder.lineHeight;
        this.backgroundColor = builder.backgroundColor;
        this.textOpacity = builder.textOpacity;
        this.seeThrough = builder.seeThrough;
        this.shadowed = builder.shadowed;
    }

    /**
     * Returns a builder pre-filled with the default style.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a builder pre-filled with this style, for deriving a modified copy.
     *
     * @return a new builder holding this style's values
     */
    public Builder toBuilder() {
        return new Builder()
                .billboard(billboard)
                .alignment(alignment)
                .scale(scale)
                .lineHeight(lineHeight)
                .backgroundColor(backgroundColor)
                .textOpacity(textOpacity)
                .seeThrough(seeThrough)
                .shadowed(shadowed);
    }

    /**
     * Returns which axes the text rotates around.
     *
     * @return the billboard constraint
     */
    public Billboard billboard() {
        return billboard;
    }

    /**
     * Returns how multiple lines are aligned against each other.
     *
     * @return the alignment
     */
    public TextAlignment alignment() {
        return alignment;
    }

    /**
     * Returns the size multiplier applied to the text.
     *
     * @return the scale, {@code 1.0} for the normal size
     */
    public float scale() {
        return scale;
    }

    /**
     * Returns the vertical gap between consecutive hologram lines, in blocks.
     *
     * @return the line height in blocks
     */
    public double lineHeight() {
        return lineHeight;
    }

    /**
     * Returns the background colour behind the text, as packed ARGB.
     *
     * @return the ARGB colour, or empty for the client default; a fully transparent value such as
     *         {@code 0x00000000} removes the background entirely
     */
    public Optional<Integer> backgroundColor() {
        return Optional.ofNullable(backgroundColor);
    }

    /**
     * Returns the text opacity.
     *
     * @return the opacity from 0 (invisible) to {@value #OPAQUE} (fully opaque)
     */
    public int textOpacity() {
        return textOpacity;
    }

    /**
     * Returns whether the text is visible through blocks.
     *
     * @return {@code true} if the text renders through terrain
     */
    public boolean seeThrough() {
        return seeThrough;
    }

    /**
     * Returns whether the text is drawn with a drop shadow.
     *
     * @return {@code true} if shadowed
     */
    public boolean shadowed() {
        return shadowed;
    }

    /**
     * Returns whether this style uses anything an armour stand fallback cannot reproduce.
     *
     * <p>An armour stand can show one line of coloured text and nothing else. Scaling, backgrounds,
     * opacity, alignment and billboard constraints all need a real text display entity, so a style
     * that changes any of them will render only approximately on a server without them.
     *
     * @return {@code true} if this style needs text display entity support
     */
    public boolean requiresTextDisplays() {
        return billboard != Billboard.CENTER
                || alignment != TextAlignment.CENTER
                || scale != 1.0f
                || backgroundColor != null
                || textOpacity != OPAQUE
                || !shadowed;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof TextDisplayStyle style
                && billboard == style.billboard
                && alignment == style.alignment
                && Float.compare(scale, style.scale) == 0
                && Double.compare(lineHeight, style.lineHeight) == 0
                && Objects.equals(backgroundColor, style.backgroundColor)
                && textOpacity == style.textOpacity
                && seeThrough == style.seeThrough
                && shadowed == style.shadowed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                billboard, alignment, scale, lineHeight, backgroundColor, textOpacity, seeThrough, shadowed);
    }

    /**
     * Returns a short description of this style.
     *
     * @return the description
     */
    @Override
    public String toString() {
        return "TextDisplayStyle[" + billboard + ", " + alignment
                + ", scale=" + scale + ", lineHeight=" + lineHeight
                + ", opacity=" + textOpacity
                + (seeThrough ? ", seeThrough" : "")
                + (shadowed ? ", shadowed" : "")
                + ']';
    }

    /**
     * Builds {@link TextDisplayStyle} instances.
     *
     * <p>Not thread safe; build on one thread and share the immutable result.
     *
     * @since 1.0.0
     */
    public static final class Builder {

        private Billboard billboard = Billboard.CENTER;
        private TextAlignment alignment = TextAlignment.CENTER;
        private float scale = 1.0f;
        private double lineHeight = 0.28;
        private Integer backgroundColor;
        private int textOpacity = OPAQUE;
        private boolean seeThrough;
        private boolean shadowed = true;

        private Builder() {
        }

        /**
         * Sets which axes the text rotates around.
         *
         * @param value the billboard constraint
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder billboard(Billboard value) {
            this.billboard = Objects.requireNonNull(value, "billboard");
            return this;
        }

        /**
         * Sets how multiple lines are aligned.
         *
         * @param value the alignment
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder alignment(TextAlignment value) {
            this.alignment = Objects.requireNonNull(value, "alignment");
            return this;
        }

        /**
         * Sets the size multiplier.
         *
         * @param value the scale, {@code 1.0} for normal size
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is not a positive finite number
         */
        public Builder scale(float value) {
            if (!Float.isFinite(value) || value <= 0.0f) {
                throw new IllegalArgumentException("Text scale must be positive and finite, was " + value);
            }
            this.scale = value;
            return this;
        }

        /**
         * Sets the vertical gap between hologram lines.
         *
         * @param value the line height in blocks
         * @throws IllegalArgumentException if {@code value} is not a positive finite number
         * @return this builder
         */
        public Builder lineHeight(double value) {
            if (!Double.isFinite(value) || value <= 0.0) {
                throw new IllegalArgumentException("Line height must be positive and finite, was " + value);
            }
            this.lineHeight = value;
            return this;
        }

        /**
         * Sets the background colour.
         *
         * @param value the packed ARGB colour, or {@code null} for the client default
         * @return this builder
         */
        public Builder backgroundColor(Integer value) {
            this.backgroundColor = value;
            return this;
        }

        /**
         * Removes the background entirely.
         *
         * @return this builder
         */
        public Builder transparentBackground() {
            this.backgroundColor = 0x00000000;
            return this;
        }

        /**
         * Sets the text opacity.
         *
         * @param value the opacity from 0 to {@value #OPAQUE}
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is outside that range
         */
        public Builder textOpacity(int value) {
            if (value < 0 || value > OPAQUE) {
                throw new IllegalArgumentException("Text opacity must be 0..255, was " + value);
            }
            this.textOpacity = value;
            return this;
        }

        /**
         * Sets whether the text renders through blocks.
         *
         * @param value {@code true} to see the text through terrain
         * @return this builder
         */
        public Builder seeThrough(boolean value) {
            this.seeThrough = value;
            return this;
        }

        /**
         * Sets whether the text is drawn with a drop shadow.
         *
         * @param value {@code true} to draw a shadow
         * @return this builder
         */
        public Builder shadowed(boolean value) {
            this.shadowed = value;
            return this;
        }

        /**
         * Builds the immutable style.
         *
         * @return the style
         */
        public TextDisplayStyle build() {
            return new TextDisplayStyle(this);
        }
    }
}
