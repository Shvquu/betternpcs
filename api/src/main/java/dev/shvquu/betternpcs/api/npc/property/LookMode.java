package dev.shvquu.betternpcs.api.npc.property;

/**
 * How an NPC decides where to look.
 *
 * @since 1.0.0
 */
public enum LookMode {

    /**
     * The NPC never turns. Its rotation is whatever its position says, and no look packets are sent
     * after the spawn.
     *
     * <p>This is the only mode that costs nothing per tick, and it is the right choice for the large
     * majority of decorative NPCs.
     */
    NONE,

    /**
     * The NPC holds a fixed rotation, re-asserting it if something moves it.
     *
     * <p>Differs from {@link #NONE} in that the rotation is actively maintained, which matters for
     * NPCs backed by a real server entity that would otherwise be turned by the vanilla AI.
     */
    FIXED,

    /**
     * The NPC turns to face the player looking at it.
     *
     * <p>Each viewer is sent a rotation computed for their own position, so every player sees the
     * NPC looking straight at them. This is what players expect from a shopkeeper, and because the
     * rotation is per-viewer it costs one small packet per tracked player per update.
     */
    LOOK_AT_VIEWER,

    /**
     * The NPC turns to face whichever player is closest, and every viewer sees the same rotation.
     *
     * <p>Cheaper than {@link #LOOK_AT_VIEWER} for crowded areas — one rotation is computed and
     * broadcast — and correct when the NPC should visibly be attending to one specific person.
     */
    LOOK_AT_NEAREST_PLAYER,

    /**
     * The NPC faces a fixed point in the world, computed once and re-sent only when the NPC or the
     * target moves.
     */
    LOOK_AT_LOCATION
}
