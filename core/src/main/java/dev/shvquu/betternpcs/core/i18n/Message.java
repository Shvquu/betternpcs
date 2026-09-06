package dev.shvquu.betternpcs.core.i18n;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Every message BetterNPCs can send, with its configuration key and its English text.
 *
 * <p>Messages are an enum rather than loose strings for three reasons. A typo in a key becomes a
 * compile error. The set of keys a language file must define is derivable, which is what
 * {@code LanguageManager} uses to report a translation that has fallen behind. And each constant
 * carries a working English fallback, so a language file missing a key produces a slightly
 * untranslated line rather than a blank message or an exception.
 *
 * <p>The text is MiniMessage. Values substituted into it are inserted as literal text, never
 * re-parsed, so a player name containing something tag-shaped cannot inject formatting or a click
 * event into a message shown to someone else.
 *
 * @since 1.0.0
 */
public enum Message {

    // --- General -----------------------------------------------------------------------------

    /** The prefix prepended to most messages. Placeholders: none. */
    PREFIX("prefix",
            "<gradient:#00c6ff:#0072ff><bold>BetterNPCs</bold></gradient> <dark_gray>»</dark_gray> "),

    /** Shown when a sender lacks the required permission. Placeholders: {@code permission}. */
    NO_PERMISSION("general.no-permission",
            "<red>You do not have permission to do that."),

    /** Shown when a console tries to run a command that needs a position. Placeholders: none. */
    PLAYERS_ONLY("general.players-only",
            "<red>Only a player can run this."),

    /** Shown for an unrecognised subcommand. Placeholders: {@code input}. */
    UNKNOWN_SUBCOMMAND("general.unknown-subcommand",
            "<red>Unknown subcommand '<white><input></white>'. Try <white>/npc help</white>."),

    /** Shown when a number could not be parsed. Placeholders: {@code input}. */
    INVALID_NUMBER("general.invalid-number",
            "<red>'<white><input></white>' is not a number."),

    /** Shown when a named world is not loaded. Placeholders: {@code world}. */
    UNKNOWN_WORLD("general.unknown-world",
            "<red>The world '<white><world></white>' is not loaded."),

    /** Header of the {@code /npc help} listing. Placeholders: none. */
    HELP_HEADER("general.help-header",
            "<gray>BetterNPCs commands <dark_gray>(only the ones you may use)"),

    /** One line of the {@code /npc help} listing. Placeholders: {@code usage}. */
    HELP_ENTRY("general.help-entry",
            "<dark_gray> <white><usage>"),

    /** Shown when a storage operation failed. Placeholders: {@code error}. */
    STORAGE_ERROR("general.storage-error",
            "<red>The database request failed. See the console for details."),

    /** Shown after a successful reload. Placeholders: {@code count}, {@code millis}. */
    RELOAD_SUCCESS("general.reload-success",
            "<green>Reloaded <white><count></white> NPCs in <white><millis></white> ms."),

    /** Shown when a reload failed. Placeholders: {@code error}. */
    RELOAD_FAILED("general.reload-failed",
            "<red>Reload failed: <white><error></white>"),

    // --- NPC lifecycle -----------------------------------------------------------------------

    /** Placeholders: {@code npc}, {@code type}. */
    NPC_CREATED("npc.created",
            "<green>Created NPC <white><npc></white> <gray>(<type>)</gray>."),

    /** Placeholders: {@code npc}. */
    NPC_DELETED("npc.deleted",
            "<green>Deleted NPC <white><npc></white>."),

    /** Placeholders: {@code npc}. */
    NPC_DELETE_CANCELLED("npc.delete-cancelled",
            "<red>Another plugin refused the deletion of <white><npc></white>."),

    /** Placeholders: {@code npc}. */
    NPC_NOT_FOUND("npc.not-found",
            "<red>There is no NPC called <white><npc></white>."),

    /** Placeholders: {@code npc}. */
    NPC_NAME_TAKEN("npc.name-taken",
            "<red>An NPC called <white><npc></white> already exists."),

    /** Placeholders: {@code npc}. */
    NPC_NAME_INVALID("npc.name-invalid",
            "<red>'<white><npc></white>' is not a usable NPC name. Use letters, digits, '_' and '-'."),

    /** Placeholders: none. */
    NPC_NO_TARGET("npc.no-target",
            "<red>Look at an NPC, or name one explicitly."),

    /** Placeholders: {@code npc}. */
    NPC_SPAWNED("npc.spawned",
            "<green>Spawned <white><npc></white>."),

    /** Placeholders: {@code npc}. */
    NPC_DESPAWNED("npc.despawned",
            "<green>Despawned <white><npc></white>."),

    /** Placeholders: {@code npc}. */
    NPC_ALREADY_SPAWNED("npc.already-spawned",
            "<yellow><npc> is already spawned."),

    /** Placeholders: {@code npc}. */
    NPC_ALREADY_DESPAWNED("npc.already-despawned",
            "<yellow><npc> is not spawned."),

    /** Placeholders: {@code npc}. */
    NPC_SPAWN_CANCELLED("npc.spawn-cancelled",
            "<red>Another plugin refused the spawn of <white><npc></white>."),

    /** Placeholders: {@code npc}, {@code world}, {@code x}, {@code y}, {@code z}. */
    NPC_TELEPORTED("npc.teleported",
            "<green>Moved <white><npc></white> to <white><x>, <y>, <z></white> in <white><world></white>."),

    /** Placeholders: {@code npc}. */
    NPC_TELEPORTED_TO_YOU("npc.teleported-to-you",
            "<green>Moved <white><npc></white> to you."),

    /** Placeholders: {@code npc}. */
    NPC_YOU_TELEPORTED("npc.you-teleported",
            "<green>Teleported you to <white><npc></white>."),

    /** Placeholders: {@code old}, {@code new}. */
    NPC_RENAMED("npc.renamed",
            "<green>Renamed <white><old></white> to <white><new></white>."),

    /** Placeholders: {@code npc}, {@code name}. */
    NPC_DISPLAY_NAME_SET("npc.display-name-set",
            "<green>Display name of <white><npc></white> is now <reset><name></reset><green>."),

    /** Placeholders: {@code npc}. */
    NPC_DISPLAY_NAME_CLEARED("npc.display-name-cleared",
            "<green>Cleared the display name of <white><npc></white>."),

    /** Placeholders: {@code npc}, {@code type}. */
    NPC_TYPE_SET("npc.type-set",
            "<green><npc> is now a <white><type></white>."),

    /** Placeholders: {@code input}. */
    NPC_TYPE_UNKNOWN("npc.type-unknown",
            "<red>'<white><input></white>' is not a usable NPC type."),

    /** Placeholders: {@code npc}. */
    NPC_SAVED("npc.saved",
            "<green>Saved <white><npc></white>."),

    /** Placeholders: {@code count}. */
    NPC_SAVED_ALL("npc.saved-all",
            "<green>Saved <white><count></white> NPCs."),

    // --- Skins -------------------------------------------------------------------------------

    /** Placeholders: {@code npc}, {@code skin}. */
    SKIN_LOADING("npc.skin.loading",
            "<gray>Fetching the skin of <white><skin></white> …"),

    /** Placeholders: {@code npc}, {@code skin}. */
    SKIN_SET("npc.skin.set",
            "<green><npc> now wears the skin of <white><skin></white>."),

    /** Placeholders: {@code npc}. */
    SKIN_CLEARED("npc.skin.cleared",
            "<green>Cleared the skin of <white><npc></white>."),

    /** Placeholders: {@code skin}, {@code error}. */
    SKIN_FAILED("npc.skin.failed",
            "<red>Could not fetch the skin of <white><skin></white>: <white><error></white>"),

    /** Placeholders: {@code skin}. */
    SKIN_NOT_FOUND("npc.skin.not-found",
            "<red>No skin was found for <white><skin></white>."),

    /** Placeholders: {@code npc}. */
    SKIN_ONLY_PLAYER_NPCS("npc.skin.only-player-npcs",
            "<red>Only player NPCs have a skin."),

    // --- Equipment ---------------------------------------------------------------------------

    /** Placeholders: {@code npc}, {@code slot}, {@code item}. */
    EQUIPMENT_SET("npc.equipment.set",
            "<green>Put <white><item></white> in the <white><slot></white> slot of <white><npc></white>."),

    /** Placeholders: {@code npc}, {@code slot}. */
    EQUIPMENT_CLEARED("npc.equipment.cleared",
            "<green>Cleared the <white><slot></white> slot of <white><npc></white>."),

    /** Placeholders: {@code input}. */
    EQUIPMENT_SLOT_UNKNOWN("npc.equipment.slot-unknown",
            "<red>'<white><input></white>' is not an equipment slot."),

    /** Placeholders: none. */
    EQUIPMENT_HOLD_ITEM("npc.equipment.hold-item",
            "<red>Hold the item you want to use."),

    // --- Actions -----------------------------------------------------------------------------

    /** Placeholders: {@code npc}, {@code interaction}, {@code action}. */
    ACTION_ADDED("npc.action.added",
            "<green>Added <white><action></white> to <white><interaction></white> on <white><npc></white>."),

    /** Placeholders: {@code npc}, {@code interaction}, {@code action}. */
    ACTION_REMOVED("npc.action.removed",
            "<green>Removed <white><action></white> from <white><npc></white>."),

    /** Placeholders: {@code npc}, {@code interaction}, {@code count}. */
    ACTION_CLEARED("npc.action.cleared",
            "<green>Removed <white><count></white> actions from <white><npc></white>."),

    /** Placeholders: {@code npc}, {@code interaction}. */
    ACTION_LIST_HEADER("npc.action.list-header",
            "<gray>Actions of <white><npc></white> for <white><interaction></white>:"),

    /** Placeholders: {@code index}, {@code action}. */
    ACTION_LIST_ENTRY("npc.action.list-entry",
            "<dark_gray> <gray><index>. <white><action>"),

    /** Placeholders: {@code npc}, {@code interaction}. */
    ACTION_LIST_EMPTY("npc.action.list-empty",
            "<gray><npc> has no actions for <white><interaction></white>."),

    /** Placeholders: {@code type}, {@code known}. */
    ACTION_TYPE_UNKNOWN("npc.action.type-unknown",
            "<red>There is no action called '<white><type></white>'. Known: <white><known></white>"),

    /** Placeholders: {@code action}, {@code error}. */
    ACTION_INVALID("npc.action.invalid",
            "<red>That action is not usable: <white><error></white>"),

    /** Placeholders: {@code index}. */
    ACTION_INDEX_OUT_OF_RANGE("npc.action.index-out-of-range",
            "<red>There is no action number <white><index></white>."),

    /** Placeholders: none. */
    ACTION_CONSOLE_DENIED("npc.action.console-denied",
            "<red>You may not add console actions. They run with full server permissions."),

    /** Placeholders: {@code input}. */
    INTERACTION_UNKNOWN("npc.action.interaction-unknown",
            "<red>'<white><input></white>' is not an interaction type."),

    // --- Listing and info --------------------------------------------------------------------

    /** Placeholders: {@code count}, {@code page}, {@code pages}. */
    LIST_HEADER("npc.list.header",
            "<gray>NPCs <dark_gray>(<white><count></white>, page <white><page></white>/<white><pages></white>)"),

    /** Placeholders: {@code npc}, {@code type}, {@code world}, {@code state}. */
    LIST_ENTRY("npc.list.entry",
            "<dark_gray> <gray><npc> <dark_gray>· <gray><type> <dark_gray>· <gray><world> <dark_gray>· <state>"),

    /** Placeholders: none. */
    LIST_EMPTY("npc.list.empty",
            "<gray>There are no NPCs yet. Create one with <white>/npc create</white>."),

    /** Placeholders: {@code page}, {@code pages}. */
    LIST_PAGE_OUT_OF_RANGE("npc.list.page-out-of-range",
            "<red>There is no page <white><page></white>; there are <white><pages></white>."),

    /** Placeholders: {@code npc}. */
    INFO_HEADER("npc.info.header",
            "<gray>NPC <white><npc></white>"),

    /** Placeholders: {@code key}, {@code value}. */
    INFO_LINE("npc.info.line",
            "<dark_gray> <gray><key><dark_gray>: <white><value>"),

    /** Placeholders: none. */
    STATE_SPAWNED("npc.state.spawned", "<green>spawned"),

    /** Placeholders: none. */
    STATE_DESPAWNED("npc.state.despawned", "<gray>despawned"),

    // --- Backup and restore ------------------------------------------------------------------

    /** Placeholders: {@code count}, {@code file}. */
    BACKUP_CREATED("backup.created",
            "<green>Backed up <white><count></white> NPCs to <white><file></white>."),

    /** Placeholders: {@code error}. */
    BACKUP_FAILED("backup.failed",
            "<red>The backup could not be written: <white><error></white>"),

    /** Placeholders: {@code input}. */
    BACKUP_NAME_INVALID("backup.name-invalid",
            "<red>'<white><input></white>' is not a usable backup name. "
                    + "Use letters, digits, <white>.</white>, <white>-</white> and <white>_</white>."),

    /** Placeholders: {@code count}. */
    BACKUP_LIST_HEADER("backup.list-header",
            "<gray>Backups <dark_gray>(<white><count></white>, newest first)"),

    /** Placeholders: {@code file}. */
    BACKUP_LIST_ENTRY("backup.list-entry",
            "<dark_gray> <gray><file>"),

    /** Placeholders: none. */
    BACKUP_LIST_EMPTY("backup.list-empty",
            "<gray>There are no backups yet. Take one with <white>/npc backup</white>."),

    /** Placeholders: {@code file}. */
    BACKUP_NOT_FOUND("backup.not-found",
            "<red>There is no backup called <white><file></white>."),

    /** Placeholders: {@code file}, {@code error}. */
    BACKUP_UNREADABLE("backup.unreadable",
            "<red><white><file></white> could not be read: <white><error></white>"),

    /** Placeholders: {@code file}, {@code created}, {@code overwritten}, {@code blocked}. */
    RESTORE_PLAN_HEADER("backup.restore.plan-header",
            "<gray>Restoring <white><file></white> would create <white><created></white>, "
                    + "overwrite <white><overwritten></white> and skip <white><blocked></white>."),

    /** Placeholders: {@code npc}. */
    RESTORE_BLOCKED_NAME_TAKEN("backup.restore.blocked-name-taken",
            "<dark_gray> <yellow><npc> <gray>· a different NPC already uses that name"),

    /** Placeholders: {@code npc}. */
    RESTORE_BLOCKED_UNUSABLE("backup.restore.blocked-unusable",
            "<dark_gray> <yellow><npc> <gray>· that is not a usable NPC name"),

    /** Placeholders: {@code count}. */
    RESTORE_PLAN_REPLACE_HINT("backup.restore.plan-replace-hint",
            "<gray><white><count></white> NPCs already exist and were left alone. "
                    + "Add <white>--replace</white> to overwrite them."),

    /** Placeholders: none. */
    RESTORE_PLAN_CONFIRM_HINT("backup.restore.plan-confirm-hint",
            "<gray>Nothing has changed yet. Run the same command with <white>--confirm</white> to apply it."),

    /** Placeholders: none. */
    RESTORE_PLAN_NO_CHANGES("backup.restore.plan-no-changes",
            "<gray>Restoring that backup would change nothing."),

    /** Placeholders: {@code created}, {@code overwritten}. */
    RESTORE_DONE("backup.restore.done",
            "<green>Restored <white><created></white> new and <white><overwritten></white> existing NPCs."),

    /** Placeholders: {@code count}. */
    RESTORE_PARTIAL("backup.restore.partial",
            "<yellow><count></yellow> NPCs could not be restored; see the server log."),

    // --- Startup -----------------------------------------------------------------------------

    /** Placeholders: {@code version}, {@code supported}. */
    UNSUPPORTED_VERSION("startup.unsupported-version",
            "<red>Minecraft <white><version></white> is not supported. This build supports <white><supported></white>."),

    /** Placeholders: {@code current}, {@code latest}, {@code url}. */
    UPDATE_AVAILABLE("startup.update-available",
            "<yellow>BetterNPCs <white><latest></white> is out; you are running <white><current></white>. "
                    + "Download: <white><url></white>");

    private static final Map<String, Message> BY_KEY = index();

    private final String key;
    private final String englishText;

    Message(String key, String englishText) {
        this.key = key;
        this.englishText = englishText;
    }

    private static Map<String, Message> index() {
        Map<String, Message> byKey = new LinkedHashMap<>();
        for (Message message : values()) {
            Message clash = byKey.put(message.key, message);
            if (clash != null) {
                // A duplicated key would silently make one of the two unreachable from a language
                // file. Failing during class initialisation makes it impossible to ship.
                throw new IllegalStateException(
                        "Duplicate message key '" + message.key + "' on " + clash + " and " + message);
            }
        }
        return Map.copyOf(byKey);
    }

    /**
     * Returns the key this message is stored under in a language file.
     *
     * @return the dotted key, for example {@code npc.created}
     */
    public String key() {
        return key;
    }

    /**
     * Returns the built-in English text, used when a language file does not define this key.
     *
     * @return the MiniMessage source
     */
    public String englishText() {
        return englishText;
    }

    /**
     * Looks a message up by key.
     *
     * @param key the dotted key
     * @return the message, or empty if no message uses that key
     */
    public static Optional<Message> byKey(String key) {
        return Optional.ofNullable(BY_KEY.get(Objects.requireNonNull(key, "key")));
    }

    /**
     * Returns every key a complete language file defines, in declaration order.
     *
     * @return an immutable list of keys
     */
    public static List<String> allKeys() {
        return Arrays.stream(values()).map(Message::key).toList();
    }
}
