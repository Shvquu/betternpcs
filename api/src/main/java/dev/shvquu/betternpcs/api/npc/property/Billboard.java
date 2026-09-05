package dev.shvquu.betternpcs.api.npc.property;

/**
 * Which axes a floating text rotates around to face the viewer.
 *
 * <p>Mirrors the vanilla text display billboard constraint. It is declared here rather than reused
 * from the server API so that the version adapters stay free to render floating text some other way
 * — an armour stand, for instance — without the public API changing shape.
 *
 * @since 1.0.0
 */
public enum Billboard {

    /** The text does not rotate, and is readable only from the direction it was placed facing. */
    FIXED,

    /** The text rotates around the vertical axis, staying upright. */
    VERTICAL,

    /** The text rotates around the horizontal axis. */
    HORIZONTAL,

    /**
     * The text always faces the viewer squarely.
     *
     * <p>The default, and what nearly every hologram wants.
     */
    CENTER
}
