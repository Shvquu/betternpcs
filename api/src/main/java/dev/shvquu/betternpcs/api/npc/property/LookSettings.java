package dev.shvquu.betternpcs.api.npc.property;

import java.util.Objects;
import java.util.Optional;

/**
 * How and how often an NPC turns to look at something.
 *
 * <p>Look tracking is the most expensive optional NPC feature: unlike a skin or a nametag, which are
 * sent once, it produces packets continuously. Every field here exists to let a server owner cap
 * that cost — {@link #range()} bounds who is considered, {@link #updateInterval()} bounds how often,
 * and {@link #rotationSpeed()} trades extra packets for a smoother turn.
 *
 * <p>Instances are immutable and safe to share across threads.
 *
 * @since 1.0.0
 */
public final class LookSettings {

    /**
     * Passed as the rotation speed to mean "snap to the target in one update".
     */
    public static final float INSTANT = 0.0f;

    /** No look tracking at all. */
    public static final LookSettings NONE = builder().build();

    private final LookMode mode;
    private final NpcPosition target;
    private final double range;
    private final float rotationSpeed;
    private final int updateInterval;
    private final boolean headOnly;

    private LookSettings(Builder builder) {
        this.mode = builder.mode;
        this.target = builder.target;
        this.range = builder.range;
        this.rotationSpeed = builder.rotationSpeed;
        this.updateInterval = builder.updateInterval;
        this.headOnly = builder.headOnly;
    }

    /**
     * Returns a builder for settings that do no look tracking.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns settings that make the NPC face each viewer individually.
     *
     * @return look settings in {@link LookMode#LOOK_AT_VIEWER} mode with the defaults
     */
    public static LookSettings lookAtViewer() {
        return builder().mode(LookMode.LOOK_AT_VIEWER).build();
    }

    /**
     * Returns settings that make the NPC face a fixed point.
     *
     * @param target the point to face
     * @return look settings in {@link LookMode#LOOK_AT_LOCATION} mode
     * @throws NullPointerException if {@code target} is {@code null}
     */
    public static LookSettings lookAt(NpcPosition target) {
        return builder()
                .mode(LookMode.LOOK_AT_LOCATION)
                .target(Objects.requireNonNull(target, "target"))
                .build();
    }

    /**
     * Returns a builder pre-filled with these settings, for deriving a modified copy.
     *
     * @return a new builder holding these values
     */
    public Builder toBuilder() {
        return new Builder()
                .mode(mode)
                .target(target)
                .range(range)
                .rotationSpeed(rotationSpeed)
                .updateInterval(updateInterval)
                .headOnly(headOnly);
    }

    /**
     * Returns how the NPC picks what to look at.
     *
     * @return the look mode
     */
    public LookMode mode() {
        return mode;
    }

    /**
     * Returns the point the NPC faces in {@link LookMode#LOOK_AT_LOCATION} mode.
     *
     * @return the target, or empty in every other mode
     */
    public Optional<NpcPosition> target() {
        return Optional.ofNullable(target);
    }

    /**
     * Returns the radius in blocks within which players are considered for look tracking.
     *
     * <p>Independent of the NPC's view distance on purpose: an NPC may well be visible from 48
     * blocks away while only turning towards players who come within 8, which is both cheaper and
     * closer to how a person behaves.
     *
     * @return the radius in blocks, always positive
     */
    public double range() {
        return range;
    }

    /**
     * Returns how far the NPC may turn per update, in degrees.
     *
     * @return the maximum degrees per update, or {@link #INSTANT} to snap to the target
     */
    public float rotationSpeed() {
        return rotationSpeed;
    }

    /**
     * Returns how many server ticks pass between look updates.
     *
     * <p>One means every tick, which is the smoothest and the most expensive. Two or three is
     * visually indistinguishable for a turning head and costs a half or a third as much.
     *
     * @return the interval in ticks, at least one
     */
    public int updateInterval() {
        return updateInterval;
    }

    /**
     * Returns whether only the head turns, leaving the body facing its stored rotation.
     *
     * @return {@code true} to rotate the head alone
     */
    public boolean headOnly() {
        return headOnly;
    }

    /**
     * Returns whether these settings require per-tick work.
     *
     * <p>The tracker uses this to skip NPCs entirely rather than scheduling a task that would find
     * nothing to do.
     *
     * @return {@code true} if the NPC has to be re-evaluated periodically
     */
    public boolean isActive() {
        return mode == LookMode.LOOK_AT_VIEWER || mode == LookMode.LOOK_AT_NEAREST_PLAYER;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof LookSettings settings
                && mode == settings.mode
                && Objects.equals(target, settings.target)
                && Double.compare(range, settings.range) == 0
                && Float.compare(rotationSpeed, settings.rotationSpeed) == 0
                && updateInterval == settings.updateInterval
                && headOnly == settings.headOnly;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, target, range, rotationSpeed, updateInterval, headOnly);
    }

    /**
     * Returns a short description of these settings.
     *
     * @return the description
     */
    @Override
    public String toString() {
        return "LookSettings[" + mode
                + (target == null ? "" : " -> " + target)
                + ", range=" + range
                + ", speed=" + (rotationSpeed == INSTANT ? "instant" : rotationSpeed + "deg")
                + ", every " + updateInterval + "t"
                + (headOnly ? ", head only" : "")
                + ']';
    }

    /**
     * Builds {@link LookSettings} instances.
     *
     * <p>Not thread safe; build on one thread and share the immutable result.
     *
     * @since 1.0.0
     */
    public static final class Builder {

        private LookMode mode = LookMode.NONE;
        private NpcPosition target;
        private double range = 16.0;
        private float rotationSpeed = INSTANT;
        private int updateInterval = 2;
        private boolean headOnly = true;

        private Builder() {
        }

        /**
         * Sets how the NPC picks what to look at.
         *
         * @param value the look mode
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder mode(LookMode value) {
            this.mode = Objects.requireNonNull(value, "mode");
            return this;
        }

        /**
         * Sets the point faced in {@link LookMode#LOOK_AT_LOCATION} mode.
         *
         * @param value the target, or {@code null} for none
         * @return this builder
         */
        public Builder target(NpcPosition value) {
            this.target = value;
            return this;
        }

        /**
         * Sets the radius within which players are considered.
         *
         * @param value the radius in blocks
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is not positive
         */
        public Builder range(double value) {
            if (!(value > 0.0)) {
                throw new IllegalArgumentException("Look range must be positive, was " + value);
            }
            this.range = value;
            return this;
        }

        /**
         * Sets how far the NPC may turn per update.
         *
         * @param value the maximum degrees per update, or {@link #INSTANT}
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is negative or above 180
         */
        public Builder rotationSpeed(float value) {
            if (value < 0.0f || value > 180.0f) {
                throw new IllegalArgumentException(
                        "Rotation speed must be between 0 and 180 degrees, was " + value);
            }
            this.rotationSpeed = value;
            return this;
        }

        /**
         * Sets how many ticks pass between look updates.
         *
         * @param value the interval in ticks
         * @return this builder
         * @throws IllegalArgumentException if {@code value} is below one
         */
        public Builder updateInterval(int value) {
            if (value < 1) {
                throw new IllegalArgumentException("Update interval must be at least 1 tick, was " + value);
            }
            this.updateInterval = value;
            return this;
        }

        /**
         * Sets whether only the head turns.
         *
         * @param value {@code true} to rotate the head alone
         * @return this builder
         */
        public Builder headOnly(boolean value) {
            this.headOnly = value;
            return this;
        }

        /**
         * Builds the immutable settings.
         *
         * @return the look settings
         * @throws IllegalStateException if the mode is {@link LookMode#LOOK_AT_LOCATION} but no
         *                               target was set
         */
        public LookSettings build() {
            if (mode == LookMode.LOOK_AT_LOCATION && target == null) {
                throw new IllegalStateException("LOOK_AT_LOCATION requires a target position");
            }
            return new LookSettings(this);
        }
    }
}
