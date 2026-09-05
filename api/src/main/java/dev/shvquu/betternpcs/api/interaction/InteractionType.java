package dev.shvquu.betternpcs.api.interaction;

/**
 * What a player did to an NPC.
 *
 * <p>The protocol distinguishes three interaction kinds — attack, interact and interact-at, the last
 * carrying the exact point on the hit box that was clicked — and reports the sneaking state
 * separately. Those five values collapse into the four cases below, which are the ones a server
 * owner actually wants to bind an action to: attack is a left click, interact and interact-at are a
 * right click, and sneaking splits each in two.
 *
 * <p>Keeping the interact-at coordinate out of this enum is deliberate: it is available on
 * {@link dev.shvquu.betternpcs.api.event.NpcInteractEvent} for the rare plugin that wants it,
 * without complicating the common case.
 *
 * @since 1.0.0
 */
public enum InteractionType {

    /** The player left-clicked or attacked the NPC. */
    LEFT_CLICK(false, false),

    /** The player right-clicked the NPC. */
    RIGHT_CLICK(true, false),

    /** The player left-clicked or attacked the NPC while sneaking. */
    SHIFT_LEFT_CLICK(false, true),

    /** The player right-clicked the NPC while sneaking. */
    SHIFT_RIGHT_CLICK(true, true);

    private final boolean rightClick;
    private final boolean sneaking;

    InteractionType(boolean rightClick, boolean sneaking) {
        this.rightClick = rightClick;
        this.sneaking = sneaking;
    }

    /**
     * Returns the interaction type for a raw click.
     *
     * @param rightClick {@code true} for a right click, {@code false} for a left click or attack
     * @param sneaking   {@code true} if the player was sneaking
     * @return the matching interaction type
     */
    public static InteractionType of(boolean rightClick, boolean sneaking) {
        if (rightClick) {
            return sneaking ? SHIFT_RIGHT_CLICK : RIGHT_CLICK;
        }
        return sneaking ? SHIFT_LEFT_CLICK : LEFT_CLICK;
    }

    /**
     * Returns whether this was a right click.
     *
     * @return {@code true} for {@link #RIGHT_CLICK} and {@link #SHIFT_RIGHT_CLICK}
     */
    public boolean isRightClick() {
        return rightClick;
    }

    /**
     * Returns whether this was a left click or an attack.
     *
     * @return {@code true} for {@link #LEFT_CLICK} and {@link #SHIFT_LEFT_CLICK}
     */
    public boolean isLeftClick() {
        return !rightClick;
    }

    /**
     * Returns whether the player was sneaking.
     *
     * @return {@code true} for the two shift variants
     */
    public boolean isSneaking() {
        return sneaking;
    }

    /**
     * Returns the same click without the sneaking modifier.
     *
     * <p>Lets an action bound to {@link #RIGHT_CLICK} also fire for {@link #SHIFT_RIGHT_CLICK} when
     * no sneaking-specific binding exists, which is what a server owner expects from a plain
     * {@code right-click} entry in a configuration file.
     *
     * @return {@link #LEFT_CLICK} or {@link #RIGHT_CLICK}
     */
    public InteractionType withoutSneaking() {
        return of(rightClick, false);
    }
}
