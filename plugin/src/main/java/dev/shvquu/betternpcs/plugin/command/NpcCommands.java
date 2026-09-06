package dev.shvquu.betternpcs.plugin.command;

import dev.shvquu.betternpcs.api.action.ActionRegistry;
import dev.shvquu.betternpcs.core.backup.BackupService;
import dev.shvquu.betternpcs.core.backup.RestoreService;
import dev.shvquu.betternpcs.core.engine.Scheduler;
import dev.shvquu.betternpcs.core.i18n.Message;
import dev.shvquu.betternpcs.core.i18n.MessageService;
import dev.shvquu.betternpcs.core.i18n.Placeholders;
import dev.shvquu.betternpcs.core.npc.DefaultNpcManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Objects;
import org.bukkit.command.CommandSender;

/**
 * Builds the {@code /npc} command tree.
 *
 * <h2>Why Brigadier</h2>
 *
 * <p>A plugin described by {@code paper-plugin.yml} has no {@code commands:} block, so Paper's
 * Brigadier API is the only way to register a command. That turns out to be the better tool anyway:
 * argument parsing, per-argument tab completion and usage errors come for free, and a node whose
 * permission check fails is hidden rather than refused — an unprivileged player sees no
 * {@code /npc} at all rather than being told they may not use it.
 *
 * <p>The API's signatures were verified identical across every supported Minecraft version before
 * being relied on; it carries an experimental annotation, and this project's whole premise is not
 * being caught out by that sort of thing.
 *
 * @since 1.0.0
 */
public final class NpcCommands {

    /**
     * The usage lines shown by {@code /npc help}, each with the permission that reveals it.
     *
     * <p>Written out rather than generated from the tree because Brigadier's own usage strings are
     * built for correctness, not for reading — and because a server owner scanning this list wants
     * the common form of each command, not every branch of it.
     */
    private static final List<HelpEntry> HELP = List.of(
            new HelpEntry("/npc create <name> [type]", Permissions.CREATE),
            new HelpEntry("/npc remove [npc]", Permissions.REMOVE),
            new HelpEntry("/npc list [page]", Permissions.LIST),
            new HelpEntry("/npc info [npc]", Permissions.INFO),
            new HelpEntry("/npc spawn [npc]", Permissions.SPAWN),
            new HelpEntry("/npc despawn [npc]", Permissions.SPAWN),
            new HelpEntry("/npc move [npc]", Permissions.MOVE),
            new HelpEntry("/npc teleport <npc>", Permissions.TELEPORT),
            new HelpEntry("/npc name <npc> [text...]", Permissions.EDIT),
            new HelpEntry("/npc rename <npc> <new>", Permissions.EDIT),
            new HelpEntry("/npc type <npc> <type>", Permissions.EDIT),
            new HelpEntry("/npc skin <npc> <player|clear>", Permissions.EDIT),
            new HelpEntry("/npc equipment <npc> <slot> [clear]", Permissions.EDIT),
            new HelpEntry("/npc action <npc> list <interaction>", Permissions.ACTION),
            new HelpEntry("/npc action <npc> add <interaction> <action...>", Permissions.ACTION),
            new HelpEntry("/npc action <npc> remove <interaction> <index>", Permissions.ACTION),
            new HelpEntry("/npc action <npc> clear <interaction>", Permissions.ACTION),
            new HelpEntry("/npc save", Permissions.SAVE),
            new HelpEntry("/npc reload", Permissions.RELOAD),
            new HelpEntry("/npc backup [name]", Permissions.BACKUP),
            new HelpEntry("/npc restore [file] [--replace] [--confirm]", Permissions.RESTORE));

    private record HelpEntry(String usage, String permission) {
    }

    private final CommandSupport support;
    private final MessageService messages;
    private final LifecycleCommands lifecycle;
    private final QueryCommands query;
    private final PositionCommands position;
    private final AppearanceCommands appearance;
    private final ActionCommands actions;
    private final BackupCommands backups;

    /**
     * Creates the command tree builder.
     *
     * @param manager        the NPC manager
     * @param messages       the message service
     * @param scheduler      used to return to the main thread after asynchronous work
     * @param actionRegistry where action handlers are looked up
     * @param backupService  writes and lists backups
     * @param restoreService applies a backup
     * @param reloadPlugin   reloads the configuration and languages
     * @throws NullPointerException if any argument is {@code null}
     */
    public NpcCommands(
            DefaultNpcManager manager,
            MessageService messages,
            Scheduler scheduler,
            ActionRegistry actionRegistry,
            BackupService backupService,
            RestoreService restoreService,
            Runnable reloadPlugin) {

        this.messages = Objects.requireNonNull(messages, "messages");
        this.support = new CommandSupport(manager, messages, scheduler);
        this.lifecycle = new LifecycleCommands(support, reloadPlugin);
        this.query = new QueryCommands(support);
        this.position = new PositionCommands(support);
        this.appearance = new AppearanceCommands(support);
        this.actions = new ActionCommands(support, actionRegistry);
        this.backups = new BackupCommands(support, backupService, restoreService);
    }

    /**
     * Registers {@code /npc} and its aliases.
     *
     * @param registrar the registrar from Paper's commands lifecycle event
     * @throws NullPointerException if {@code registrar} is {@code null}
     */
    public void register(Commands registrar) {
        Objects.requireNonNull(registrar, "registrar");

        registrar.register(
                build(),
                "Create and manage NPCs",
                List.of("npcs", "betternpcs"));
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("npc")
                .requires(source -> source.getSender().hasPermission(Permissions.COMMAND))
                // Bare /npc shows the help, which is what someone who has forgotten the syntax types.
                .executes(context -> help(context.getSource().getSender()))
                .then(Commands.literal("help")
                        .executes(context -> help(context.getSource().getSender())))
                .then(lifecycle.create())
                .then(lifecycle.remove())
                // `delete` is the word people reach for after `create`, so both work.
                .then(alias("delete", lifecycle.remove()))
                .then(lifecycle.spawn())
                .then(lifecycle.despawn())
                .then(lifecycle.save())
                .then(lifecycle.reload())
                .then(query.list())
                .then(query.info())
                .then(position.move())
                .then(position.teleport())
                .then(alias("tp", position.teleport()))
                .then(appearance.displayName())
                .then(appearance.rename())
                .then(appearance.type())
                .then(appearance.skin())
                .then(appearance.equipment())
                .then(actions.action())
                .then(backups.backup())
                .then(backups.restore())
                .build();
    }

    /**
     * Returns a copy of a subcommand under a different literal.
     *
     * <p>Brigadier has no notion of an alias for a child node, and re-rooting the same node object
     * would make it appear twice in one tree. Rebuilding under the new name is the supported way.
     *
     * @param name    the alias
     * @param wrapped the subcommand to mirror
     * @return the aliased builder
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> alias(
            String name,
            com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> wrapped) {

        com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> aliased =
                Commands.literal(name).requires(wrapped.getRequirement());

        if (wrapped.getCommand() != null) {
            aliased.executes(wrapped.getCommand());
        }
        wrapped.getArguments().forEach(aliased::then);
        return aliased;
    }

    private int help(CommandSender sender) {
        messages.send(sender, Message.HELP_HEADER);
        for (HelpEntry entry : HELP) {
            if (sender.hasPermission(entry.permission())) {
                messages.sendUnprefixed(sender, Message.HELP_ENTRY,
                        Placeholders.text("usage", entry.usage()));
            }
        }
        return CommandSupport.SUCCESS;
    }
}
