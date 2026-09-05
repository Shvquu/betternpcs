package dev.shvquu.betternpcs.api.action;

/**
 * The ids of the action handlers BetterNPCs registers itself.
 *
 * <p>Referencing a constant from here instead of writing the string keeps a typo a compile error,
 * and makes it obvious which ids an extension must not try to claim.
 *
 * @since 1.0.0
 */
public final class BuiltinActions {

    /**
     * Sends the argument to the interacting player as MiniMessage.
     *
     * <p>Example: {@code message: <green>Welcome, <player_name>!}
     */
    public static final String MESSAGE = "message";

    /**
     * Sends the argument to every online player as MiniMessage.
     *
     * <p>Example: {@code broadcast: <yellow><player_name> found the secret NPC!}
     */
    public static final String BROADCAST = "broadcast";

    /**
     * Shows the argument in the interacting player's action bar.
     *
     * <p>Example: {@code actionbar: <gray>Right-click again to confirm}
     */
    public static final String ACTIONBAR = "actionbar";

    /**
     * Shows a title to the interacting player.
     *
     * <p>The argument is the title and the subtitle separated by {@code |}, either of which may be
     * empty.
     *
     * <p>Example: {@code title: <gold>Welcome|<gray>to the shop}
     */
    public static final String TITLE = "title";

    /**
     * Runs the argument as a command, as the interacting player.
     *
     * <p>The command runs with the player's own permissions — this action cannot be used to give a
     * player access to something they could not already run themselves. Use
     * {@link #CONSOLE_COMMAND} when elevated rights are the point.
     *
     * <p>Example: {@code command: spawn}
     */
    public static final String PLAYER_COMMAND = "command";

    /**
     * Runs the argument as a command from the console.
     *
     * <p>Runs with full permissions, so an NPC carrying this action is as powerful as the person who
     * configured it. Editing NPC actions is therefore gated behind {@code betternpcs.action.console}
     * separately from the general edit permission.
     *
     * <p>Example: {@code console: give <player_name> diamond 1}
     */
    public static final String CONSOLE_COMMAND = "console";

    /**
     * Plays a sound to the interacting player.
     *
     * <p>The argument is a sound key, optionally followed by a volume and a pitch separated by
     * spaces.
     *
     * <p>Example: {@code sound: entity.player.levelup 1.0 1.2}
     */
    public static final String SOUND = "sound";

    /**
     * Shows particles at the NPC.
     *
     * <p>The argument is a particle key, optionally followed by a count and an offset.
     *
     * <p>Example: {@code particle: happy_villager 10 0.5}
     */
    public static final String PARTICLE = "particle";

    /**
     * Teleports the interacting player.
     *
     * <p>The argument is {@code world x y z} with an optional {@code yaw pitch}.
     *
     * <p>Example: {@code teleport: world 100 64 100 90 0}
     */
    public static final String TELEPORT = "teleport";

    /**
     * Plays an animation on the NPC, visible to the interacting player.
     *
     * <p>The argument is a {@link dev.shvquu.betternpcs.api.animation.NpcAnimation} name.
     *
     * <p>Example: {@code animation: SWING_MAIN_HAND}
     */
    public static final String ANIMATION = "animation";

    /**
     * Stops the rest of the chain unless the player holds a permission.
     *
     * <p>Example: {@code require-permission: myserver.vip}
     */
    public static final String REQUIRE_PERMISSION = "require-permission";

    /**
     * Stops the rest of the chain if the player has interacted with this NPC too recently.
     *
     * <p>The argument is the cooldown in seconds. Placing it first in a chain is how an NPC is
     * protected against click spam.
     *
     * <p>Example: {@code cooldown: 3}
     */
    public static final String COOLDOWN = "cooldown";

    private BuiltinActions() {
        throw new AssertionError("No instances");
    }
}
