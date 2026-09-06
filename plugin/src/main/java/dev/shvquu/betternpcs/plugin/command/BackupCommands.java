package dev.shvquu.betternpcs.plugin.command;

import static dev.shvquu.betternpcs.plugin.command.CommandSupport.FAILURE;
import static dev.shvquu.betternpcs.plugin.command.CommandSupport.SUCCESS;
import static dev.shvquu.betternpcs.plugin.command.CommandSupport.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import dev.shvquu.betternpcs.core.backup.BackupFile;
import dev.shvquu.betternpcs.core.backup.BackupService;
import dev.shvquu.betternpcs.core.backup.RestoreService;
import dev.shvquu.betternpcs.core.i18n.Message;
import dev.shvquu.betternpcs.core.i18n.Placeholders;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.bukkit.command.CommandSender;

/**
 * {@code /npc backup} and {@code /npc restore}.
 *
 * <h2>Restoring asks first</h2>
 *
 * <p>{@code /npc restore <file>} on its own is a <em>dry run</em>: it says what would be created,
 * what would be overwritten and what would be skipped, and changes nothing. {@code --confirm}
 * applies it. Nobody should have to find out what a restore does by running one.
 *
 * <p>{@code --replace} is the second gate. Without it an NPC that already exists is left exactly as
 * it is; with it, the version in the file wins. Neither option ever deletes an NPC that the backup
 * does not mention — see {@link RestoreService}.
 *
 * <h2>Files are read and written off the main thread</h2>
 *
 * <p>Both are disk I/O, and a backup of a few thousand NPCs is not instant. Taking the snapshots and
 * applying a restore touch live NPC state and hop back onto the main thread first; the reading and
 * writing in between does not.
 *
 * @since 1.0.0
 */
final class BackupCommands {

    private static final String FILE_ARGUMENT = "file";
    private static final String NAME_ARGUMENT = "name";
    private static final String REPLACE_FLAG = "--replace";
    private static final String CONFIRM_FLAG = "--confirm";
    private static final List<String> FLAGS = List.of(REPLACE_FLAG, CONFIRM_FLAG);

    private final CommandSupport support;
    private final BackupService backups;
    private final RestoreService restores;

    /**
     * The file names offered in tab completion.
     *
     * <p>Cached rather than listed on demand, because Paper handles a tab-completion packet on the
     * main thread and a completion is not worth a disk read there. It is refreshed asynchronously
     * whenever a backup command runs, so it goes stale only if somebody drops a file into the folder
     * by hand — in which case they can type the name, and the next command picks it up.
     */
    private volatile List<String> cachedNames = List.of();

    /**
     * Creates the subcommands.
     *
     * @param support  shared command plumbing
     * @param backups  the backups folder
     * @param restores applies a backup
     * @throws NullPointerException if any argument is {@code null}
     */
    BackupCommands(CommandSupport support, BackupService backups, RestoreService restores) {
        this.support = Objects.requireNonNull(support, "support");
        this.backups = Objects.requireNonNull(backups, "backups");
        this.restores = Objects.requireNonNull(restores, "restores");
        refreshNames();
    }

    // ---------------------------------------------------------------------------------------------
    // /npc backup [name]
    // ---------------------------------------------------------------------------------------------

    LiteralArgumentBuilder<CommandSourceStack> backup() {
        return literal("backup", Permissions.BACKUP)
                .executes(context -> backup(context, null))
                // No suggestions: the name is whatever the sender wants to call it, and Brigadier
                // offers nothing for a bare string argument.
                .then(Commands.argument(NAME_ARGUMENT, StringArgumentType.word())
                        .executes(context -> backup(
                                context, StringArgumentType.getString(context, NAME_ARGUMENT))));
    }

    private int backup(CommandContext<CommandSourceStack> context, String name) {
        if (name != null && !BackupFile.isValidName(name)) {
            // Checked here as well as in BackupService so the sender gets a localised message rather
            // than an exception stack in the console.
            support.send(context, Message.BACKUP_NAME_INVALID, Placeholders.text("input", name));
            return FAILURE;
        }

        CommandSender sender = context.getSource().getSender();
        // Snapshots first, on the thread the command arrived on, because they read live NPC state.
        List<NpcSnapshot> snapshots = BackupService.snapshotEverything(support.manager());

        support.async(() -> {
            try {
                var written = backups.write(name, snapshots);
                refreshNames();
                support.messages().send(sender, Message.BACKUP_CREATED,
                        Placeholders.number("count", snapshots.size()),
                        Placeholders.text("file", written.getFileName().toString()));
            } catch (IOException | RuntimeException failure) {
                support.messages().send(sender, Message.BACKUP_FAILED,
                        Placeholders.text("error", describe(failure)));
            }
        });
        return SUCCESS;
    }

    // ---------------------------------------------------------------------------------------------
    // /npc restore [file] [--replace] [--confirm]
    // ---------------------------------------------------------------------------------------------

    LiteralArgumentBuilder<CommandSourceStack> restore() {
        LiteralArgumentBuilder<CommandSourceStack> root =
                literal("restore", Permissions.RESTORE)
                        // No file named: show what there is to restore. Which is what you want to
                        // read immediately before typing the next command anyway.
                        .executes(this::list);

        var file = Commands.argument(FILE_ARGUMENT, StringArgumentType.word())
                .suggests(backupNames());
        return root.then(withFlags(file, Set.of()));
    }

    /**
     * Adds the flag literals to a node, recursively, so that any order and any subset completes.
     *
     * <p>Literal children rather than a greedy string, because Brigadier then offers the flags in tab
     * completion and refuses a misspelt one instead of silently ignoring it — and a silently ignored
     * {@code --confirm} would be a restore that did nothing while looking like it worked.
     */
    private <T extends ArgumentBuilder<CommandSourceStack, T>> T withFlags(T node, Set<String> given) {
        node.executes(context -> restore(context, given));
        for (String flag : FLAGS) {
            if (!given.contains(flag)) {
                Set<String> next = new LinkedHashSet<>(given);
                next.add(flag);
                node.then(withFlags(Commands.literal(flag), Set.copyOf(next)));
            }
        }
        return node;
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        support.async(() -> {
            List<String> names;
            try {
                names = backups.list();
            } catch (IOException failure) {
                support.messages().send(sender, Message.BACKUP_FAILED,
                        Placeholders.text("error", describe(failure)));
                return;
            }
            cachedNames = names;

            if (names.isEmpty()) {
                support.messages().send(sender, Message.BACKUP_LIST_EMPTY);
                return;
            }
            support.messages().send(sender, Message.BACKUP_LIST_HEADER,
                    Placeholders.number("count", names.size()));
            for (String name : names) {
                support.messages().sendUnprefixed(sender, Message.BACKUP_LIST_ENTRY,
                        Placeholders.text("file", name));
            }
        });
        return SUCCESS;
    }

    private int restore(CommandContext<CommandSourceStack> context, Set<String> flags) {
        String file = StringArgumentType.getString(context, FILE_ARGUMENT);
        boolean replace = flags.contains(REPLACE_FLAG);
        boolean confirm = flags.contains(CONFIRM_FLAG);

        if (!BackupFile.isValidName(stripExtension(file))) {
            support.send(context, Message.BACKUP_NAME_INVALID, Placeholders.text("input", file));
            return FAILURE;
        }

        CommandSender sender = context.getSource().getSender();
        support.async(() -> {
            BackupFile backup;
            try {
                backup = backups.read(file);
            } catch (NoSuchFileException missing) {
                support.messages().send(sender, Message.BACKUP_NOT_FOUND,
                        Placeholders.text("file", file));
                return;
            } catch (IOException | RuntimeException unreadable) {
                support.messages().send(sender, Message.BACKUP_UNREADABLE,
                        Placeholders.text("file", file),
                        Placeholders.text("error", describe(unreadable)));
                return;
            }

            // Planning and applying both read and write the NPC registry, so both belong on the main
            // thread. Only the file read above was async.
            support.onMain(() -> planAndMaybeApply(sender, file, backup, replace, confirm));
        });
        return SUCCESS;
    }

    private void planAndMaybeApply(
            CommandSender sender, String file, BackupFile backup, boolean replace, boolean confirm) {

        RestoreService.Plan plan = restores.plan(backup);

        if (!confirm) {
            report(sender, file, plan, replace);
            return;
        }

        RestoreService.Result result = restores.apply(plan, replace);
        support.messages().send(sender, Message.RESTORE_DONE,
                Placeholders.number("created", result.created()),
                Placeholders.number("overwritten", result.overwritten()));

        if (!replace && !plan.overwritten().isEmpty()) {
            support.messages().sendUnprefixed(sender, Message.RESTORE_PLAN_REPLACE_HINT,
                    Placeholders.number("count", plan.overwritten().size()));
        }
        blocked(sender, plan);
        if (result.failed() > 0) {
            support.messages().sendUnprefixed(sender, Message.RESTORE_PARTIAL,
                    Placeholders.number("count", result.failed()));
        }
    }

    private void report(
            CommandSender sender, String file, RestoreService.Plan plan, boolean replace) {

        support.messages().send(sender, Message.RESTORE_PLAN_HEADER,
                Placeholders.text("file", file),
                Placeholders.number("created", plan.created().size()),
                Placeholders.number("overwritten", replace ? plan.overwritten().size() : 0),
                Placeholders.number("blocked",
                        plan.blocked().size() + (replace ? 0 : plan.overwritten().size())));

        blocked(sender, plan);

        if (!replace && !plan.overwritten().isEmpty()) {
            support.messages().sendUnprefixed(sender, Message.RESTORE_PLAN_REPLACE_HINT,
                    Placeholders.number("count", plan.overwritten().size()));
        }

        if (plan.changesAnything(replace)) {
            support.messages().sendUnprefixed(sender, Message.RESTORE_PLAN_CONFIRM_HINT);
        } else {
            support.messages().sendUnprefixed(sender, Message.RESTORE_PLAN_NO_CHANGES);
        }
    }

    private void blocked(CommandSender sender, RestoreService.Plan plan) {
        for (RestoreService.Blocked entry : plan.blocked()) {
            Message message = switch (entry.conflict()) {
                case NAME_TAKEN -> Message.RESTORE_BLOCKED_NAME_TAKEN;
                case UNUSABLE -> Message.RESTORE_BLOCKED_UNUSABLE;
            };
            support.messages().sendUnprefixed(sender, message,
                    Placeholders.text("npc", entry.snapshot().name()));
        }
    }

    // ---------------------------------------------------------------------------------------------

    private SuggestionProvider<CommandSourceStack> backupNames() {
        return (context, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String name : cachedNames) {
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        };
    }

    private void refreshNames() {
        support.async(() -> {
            try {
                cachedNames = backups.list();
            } catch (IOException unreadable) {
                // Tab completion is a convenience. A folder that cannot be listed is reported by the
                // command that actually needs it, not by a completion nobody asked for.
                cachedNames = List.of();
            }
        });
    }

    private static String stripExtension(String file) {
        return file.endsWith(BackupFile.EXTENSION)
                ? file.substring(0, file.length() - BackupFile.EXTENSION.length())
                : file;
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return message == null ? failure.getClass().getSimpleName() : message;
    }
}
