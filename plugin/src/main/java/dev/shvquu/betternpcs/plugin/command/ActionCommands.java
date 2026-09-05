package dev.shvquu.betternpcs.plugin.command;

import static dev.shvquu.betternpcs.plugin.command.CommandSupport.FAILURE;
import static dev.shvquu.betternpcs.plugin.command.CommandSupport.SUCCESS;
import static dev.shvquu.betternpcs.plugin.command.CommandSupport.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.shvquu.betternpcs.api.action.ActionDefinition;
import dev.shvquu.betternpcs.api.action.ActionRegistry;
import dev.shvquu.betternpcs.api.action.BuiltinActions;
import dev.shvquu.betternpcs.api.action.NpcActionHandler;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.core.i18n.Message;
import dev.shvquu.betternpcs.core.i18n.Placeholders;
import dev.shvquu.betternpcs.core.npc.NpcHandle;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Editing an NPC's action chains.
 *
 * <p>{@code /npc action <npc> add|remove|list|clear <interaction> …}
 *
 * @since 1.0.0
 */
final class ActionCommands {

    private final CommandSupport support;
    private final ActionRegistry registry;

    /**
     * Creates the subcommands.
     *
     * @param support  shared command plumbing
     * @param registry where action handlers are looked up, for validation and tab completion
     * @throws NullPointerException if either argument is {@code null}
     */
    ActionCommands(CommandSupport support, ActionRegistry registry) {
        this.support = Objects.requireNonNull(support, "support");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    LiteralArgumentBuilder<CommandSourceStack> action() {
        return literal("action", Permissions.ACTION)
                .then(support.npcArgument()
                        .then(Commands.literal("list")
                                .then(interaction().executes(this::list)))
                        .then(Commands.literal("add")
                                .then(interaction()
                                        .then(Commands.argument("action", StringArgumentType.greedyString())
                                                .suggests(actionTypes())
                                                .executes(this::add))))
                        .then(Commands.literal("remove")
                                .then(interaction()
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(this::remove))))
                        .then(Commands.literal("clear")
                                .then(interaction().executes(this::clear))));
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> interaction() {
        return Commands.argument("interaction", StringArgumentType.word())
                .suggests(CommandSupport.suggesting(interactionNames()));
    }

    private static List<String> interactionNames() {
        return Arrays.stream(InteractionType.values())
                .map(type -> type.name().toLowerCase(Locale.ROOT))
                .toList();
    }

    private com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> actionTypes() {
        return (context, builder) -> {
            // Only useful before a colon has been typed: after that the argument is the handler's
            // business and BetterNPCs has nothing sensible to offer.
            if (builder.getRemaining().indexOf(':') < 0) {
                String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
                registry.handlers().stream()
                        .map(NpcActionHandler::id)
                        .filter(id -> id.startsWith(prefix))
                        .sorted()
                        .forEach(id -> builder.suggest(id + ": "));
            }
            return builder.buildFuture();
        };
    }

    // ---------------------------------------------------------------------------------------------

    private int list(CommandContext<CommandSourceStack> context) {
        Optional<NpcHandle> target = support.resolve(context);
        Optional<InteractionType> interaction = interactionOf(context);
        if (target.isEmpty() || interaction.isEmpty()) {
            return FAILURE;
        }

        NpcHandle npc = target.get();
        List<ActionDefinition> actions = npc.actions(interaction.get());
        String interactionName = interaction.get().name().toLowerCase(Locale.ROOT);

        if (actions.isEmpty()) {
            support.send(context, Message.ACTION_LIST_EMPTY,
                    Placeholders.text("npc", npc.name()),
                    Placeholders.text("interaction", interactionName));
            return SUCCESS;
        }

        support.send(context, Message.ACTION_LIST_HEADER,
                Placeholders.text("npc", npc.name()),
                Placeholders.text("interaction", interactionName));

        for (int i = 0; i < actions.size(); i++) {
            support.sendPlain(context, Message.ACTION_LIST_ENTRY,
                    // One-based, matching what `/npc action … remove <index>` expects.
                    Placeholders.number("index", i + 1),
                    Placeholders.text("action", actions.get(i).serialize()));
        }
        return SUCCESS;
    }

    private int add(CommandContext<CommandSourceStack> context) {
        Optional<NpcHandle> target = support.resolve(context);
        Optional<InteractionType> interaction = interactionOf(context);
        if (target.isEmpty() || interaction.isEmpty()) {
            return FAILURE;
        }

        String raw = StringArgumentType.getString(context, "action");
        ActionDefinition definition;
        try {
            definition = ActionDefinition.parse(raw);
        } catch (IllegalArgumentException malformed) {
            support.send(context, Message.ACTION_INVALID,
                    Placeholders.text("action", raw),
                    Placeholders.text("error", String.valueOf(malformed.getMessage())));
            return FAILURE;
        }

        if (isConsoleAction(definition)
                && !context.getSource().getSender().hasPermission(Permissions.ACTION_CONSOLE)) {
            // A console action runs with full server permissions. Being allowed to edit NPCs is not
            // the same as being allowed to make one run /op.
            support.send(context, Message.ACTION_CONSOLE_DENIED);
            return FAILURE;
        }

        NpcHandle npc = target.get();
        try {
            npc.addAction(interaction.get(), definition);
        } catch (IllegalArgumentException rejected) {
            Message reason = registry.isRegistered(definition.type())
                    ? Message.ACTION_INVALID
                    : Message.ACTION_TYPE_UNKNOWN;
            support.send(context, reason,
                    Placeholders.text("type", definition.type()),
                    Placeholders.text("action", definition.serialize()),
                    Placeholders.text("error", String.valueOf(rejected.getMessage())),
                    Placeholders.text("known", knownActionNames()));
            return FAILURE;
        }

        support.send(context, Message.ACTION_ADDED,
                Placeholders.text("npc", npc.name()),
                Placeholders.text("interaction", interaction.get().name().toLowerCase(Locale.ROOT)),
                Placeholders.text("action", definition.serialize()));
        return SUCCESS;
    }

    private static boolean isConsoleAction(ActionDefinition definition) {
        return definition.type().equals(BuiltinActions.CONSOLE_COMMAND)
                || definition.type().equals("console-command");
    }

    private int remove(CommandContext<CommandSourceStack> context) {
        Optional<NpcHandle> target = support.resolve(context);
        Optional<InteractionType> interaction = interactionOf(context);
        if (target.isEmpty() || interaction.isEmpty()) {
            return FAILURE;
        }

        int index = IntegerArgumentType.getInteger(context, "index");
        NpcHandle npc = target.get();

        ActionDefinition removed;
        try {
            // The command is one-based; the API is zero-based.
            removed = npc.removeAction(interaction.get(), index - 1);
        } catch (IndexOutOfBoundsException outOfRange) {
            support.send(context, Message.ACTION_INDEX_OUT_OF_RANGE, Placeholders.number("index", index));
            return FAILURE;
        }

        support.send(context, Message.ACTION_REMOVED,
                Placeholders.text("npc", npc.name()),
                Placeholders.text("interaction", interaction.get().name().toLowerCase(Locale.ROOT)),
                Placeholders.text("action", removed.serialize()));
        return SUCCESS;
    }

    private int clear(CommandContext<CommandSourceStack> context) {
        Optional<NpcHandle> target = support.resolve(context);
        Optional<InteractionType> interaction = interactionOf(context);
        if (target.isEmpty() || interaction.isEmpty()) {
            return FAILURE;
        }

        NpcHandle npc = target.get();
        int count = npc.actions(interaction.get()).size();
        npc.clearActions(interaction.get());

        support.send(context, Message.ACTION_CLEARED,
                Placeholders.text("npc", npc.name()),
                Placeholders.text("interaction", interaction.get().name().toLowerCase(Locale.ROOT)),
                Placeholders.number("count", count));
        return SUCCESS;
    }

    // ---------------------------------------------------------------------------------------------

    private Optional<InteractionType> interactionOf(CommandContext<CommandSourceStack> context) {
        String raw = StringArgumentType.getString(context, "interaction");
        try {
            return Optional.of(InteractionType.valueOf(raw.toUpperCase(Locale.ROOT).replace('-', '_')));
        } catch (IllegalArgumentException unknown) {
            support.send(context, Message.INTERACTION_UNKNOWN, Placeholders.text("input", raw));
            return Optional.empty();
        }
    }

    private String knownActionNames() {
        return registry.handlers().stream()
                .map(NpcActionHandler::id)
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
