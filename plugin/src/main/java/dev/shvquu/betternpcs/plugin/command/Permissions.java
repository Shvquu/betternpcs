package dev.shvquu.betternpcs.plugin.command;

/**
 * The permission nodes BetterNPCs checks.
 *
 * <p>One node per action rather than a single {@code betternpcs.use}, so that a server can hand out
 * the ability to look at NPCs without the ability to delete them. {@link #ADMIN} is declared in
 * {@code paper-plugin.yml} as the parent of every other node, so granting it grants the lot.
 *
 * <p>Two nodes are worth singling out. {@link #COMMAND} gates the command as a whole, which is what
 * makes an unprivileged player see no {@code /npc} in their tab completion at all. And
 * {@link #ACTION_CONSOLE} is a second gate on top of {@link #ACTION}, because a console action runs
 * with full server permissions — someone allowed to edit NPCs is not automatically someone allowed
 * to make one run {@code /op}.
 *
 * @since 1.0.0
 */
public final class Permissions {

    /** Grants every other node. Declared as their parent in {@code paper-plugin.yml}. */
    public static final String ADMIN = "betternpcs.admin";

    /** Required to use {@code /npc} at all. */
    public static final String COMMAND = "betternpcs.command";

    /** Create NPCs. */
    public static final String CREATE = "betternpcs.command.create";

    /** Delete NPCs. */
    public static final String REMOVE = "betternpcs.command.remove";

    /** List NPCs. */
    public static final String LIST = "betternpcs.command.list";

    /** Inspect an NPC. */
    public static final String INFO = "betternpcs.command.info";

    /** Spawn and despawn NPCs. */
    public static final String SPAWN = "betternpcs.command.spawn";

    /** Teleport yourself to an NPC. */
    public static final String TELEPORT = "betternpcs.command.teleport";

    /** Move an NPC. */
    public static final String MOVE = "betternpcs.command.move";

    /** Change an NPC's name, type, skin or equipment. */
    public static final String EDIT = "betternpcs.command.edit";

    /** Change an NPC's actions. */
    public static final String ACTION = "betternpcs.command.action";

    /**
     * Add a {@code console:} action.
     *
     * <p>Deliberately not implied by {@link #ACTION}. A console action runs with full server
     * permissions, so an NPC carrying one is as powerful as whoever configured it.
     */
    public static final String ACTION_CONSOLE = "betternpcs.command.action.console";

    /** Reload the configuration and NPCs. */
    public static final String RELOAD = "betternpcs.command.reload";

    /** Force a save. */
    public static final String SAVE = "betternpcs.command.save";

    /** Write a backup, and list the backups that exist. */
    public static final String BACKUP = "betternpcs.command.backup";

    /**
     * Restore a backup.
     *
     * <p>Separate from {@link #BACKUP}, because taking one is harmless and applying one overwrites
     * live NPCs.
     */
    public static final String RESTORE = "betternpcs.command.restore";

    private Permissions() {
        throw new AssertionError("No instances");
    }
}
