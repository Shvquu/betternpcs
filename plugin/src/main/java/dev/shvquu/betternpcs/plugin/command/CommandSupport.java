package dev.shvquu.betternpcs.plugin.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.shvquu.betternpcs.core.engine.Scheduler;
import dev.shvquu.betternpcs.core.i18n.Message;
import dev.shvquu.betternpcs.core.i18n.MessageService;
import dev.shvquu.betternpcs.core.i18n.Placeholders;
import dev.shvquu.betternpcs.core.npc.DefaultNpcManager;
import dev.shvquu.betternpcs.core.npc.NpcHandle;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The pieces every {@code /npc} subcommand needs: resolving which NPC was meant, replying, and
 * declaring a permission.
 *
 * <p>Extracted so that the subcommand classes contain their own logic and nothing else. The
 * interesting piece is {@link #resolve}: most subcommands take the NPC's name, but a player standing
 * in front of one should not have to type it, so every one of them also works with no name at all
 * and falls back to whatever the player is looking at.
 *
 * @since 1.0.0
 */
public final class CommandSupport {

    /** How far the "which NPC am I looking at" search reaches, in blocks. */
    public static final double TARGET_RANGE = 8.0;

    /** The Brigadier argument name used for an NPC everywhere in the tree. */
    public static final String NPC_ARGUMENT = "npc";

    /** Brigadier's convention: a positive result means the command did something. */
    public static final int SUCCESS = 1;

    /** Brigadier's convention: zero means it did not. */
    public static final int FAILURE = 0;

    private final DefaultNpcManager manager;
    private final MessageService messages;
    private final Scheduler scheduler;

    /**
     * Creates the helper.
     *
     * @param manager   the NPC manager
     * @param messages  the message service
     * @param scheduler used to get back onto the main thread after an asynchronous storage call
     * @throws NullPointerException if any argument is {@code null}
     */
    public CommandSupport(DefaultNpcManager manager, MessageService messages, Scheduler scheduler) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Runs work on the main server thread.
     *
     * <p>Storage futures complete on a background thread, and anything that touches the engine
     * afterwards has to come back first. Sending a message does not, but spawning an NPC does.
     *
     * @param work what to run
     */
    public void onMain(Runnable work) {
        scheduler.runOnMainThread(work);
    }

    /**
     * Runs work off the main server thread.
     *
     * <p>For the blocking parts of a command — reading and writing backup files, mostly. Anything
     * that then touches NPC state has to come back through {@link #onMain(Runnable)}; sending a
     * message does not.
     *
     * @param work what to run
     */
    public void async(Runnable work) {
        scheduler.runAsync(work);
    }

    /**
     * Returns the NPC manager.
     *
     * @return the manager
     */
    public DefaultNpcManager manager() {
        return manager;
    }

    /**
     * Returns the message service.
     *
     * @return the messages
     */
    public MessageService messages() {
        return messages;
    }

    // ---------------------------------------------------------------------------------------------
    // Argument building
    // ---------------------------------------------------------------------------------------------

    /**
     * Returns a literal node gated behind a permission.
     *
     * <p>Brigadier hides nodes whose {@code requires} predicate fails, so an unprivileged player does
     * not merely get refused — the subcommand does not appear in their tab completion at all.
     *
     * @param name       the literal
     * @param permission the node required to see and use it
     * @return the builder
     */
    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name, String permission) {
        return Commands.literal(name)
                .requires(source -> source.getSender().hasPermission(permission));
    }

    /**
     * Returns the NPC name argument, completing over the NPCs that exist.
     *
     * @return the builder
     */
    public RequiredArgumentBuilder<CommandSourceStack, String> npcArgument() {
        return Commands.argument(NPC_ARGUMENT, StringArgumentType.word()).suggests(npcNames());
    }

    /**
     * Returns a suggestion provider over existing NPC names.
     *
     * @return the provider
     */
    public SuggestionProvider<CommandSourceStack> npcNames() {
        return (context, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            manager.completeNames(prefix).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    /**
     * Returns a suggestion provider over a fixed set of values.
     *
     * @param values the values to offer
     * @return the provider
     */
    public static SuggestionProvider<CommandSourceStack> suggesting(Iterable<String> values) {
        return (context, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String value : values) {
                if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(value);
                }
            }
            return builder.buildFuture();
        };
    }

    /**
     * Returns an already-completed empty suggestion list.
     *
     * @param <T> the suggestion type, unused
     * @return an empty completed future
     */
    public static <T> CompletableFuture<T> noSuggestions() {
        return CompletableFuture.completedFuture(null);
    }

    // ---------------------------------------------------------------------------------------------
    // Resolution
    // ---------------------------------------------------------------------------------------------

    /**
     * Resolves the NPC named by the {@code npc} argument, reporting the failure itself.
     *
     * @param context the command context, which must have an {@code npc} argument
     * @return the NPC, or empty if no NPC has that name
     */
    public Optional<NpcHandle> resolve(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, NPC_ARGUMENT);
        Optional<NpcHandle> npc = manager.handleByName(name);
        if (npc.isEmpty()) {
            send(context, Message.NPC_NOT_FOUND, Placeholders.text("npc", name));
        }
        return npc;
    }

    /**
     * Resolves the NPC the sender is looking at, reporting the failure itself.
     *
     * <p>Only a player can look at anything, so the console is told to name one explicitly.
     *
     * @param context the command context
     * @return the NPC, or empty if the sender is not a player or is not looking at an NPC
     */
    public Optional<NpcHandle> resolveTargeted(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            send(context, Message.PLAYERS_ONLY);
            return Optional.empty();
        }
        Optional<NpcHandle> npc = manager.targetedBy(player, TARGET_RANGE)
                .map(NpcHandle.class::cast);
        if (npc.isEmpty()) {
            send(context, Message.NPC_NO_TARGET);
        }
        return npc;
    }

    /**
     * Returns the sender as a player, reporting the failure itself.
     *
     * @param context the command context
     * @return the player, or empty if the console ran the command
     */
    public Optional<Player> requirePlayer(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (sender instanceof Player player) {
            return Optional.of(player);
        }
        send(context, Message.PLAYERS_ONLY);
        return Optional.empty();
    }

    // ---------------------------------------------------------------------------------------------
    // Replying
    // ---------------------------------------------------------------------------------------------

    /**
     * Sends a message to whoever ran the command.
     *
     * @param context      the command context
     * @param message      the message
     * @param placeholders the values to substitute
     */
    public void send(
            CommandContext<CommandSourceStack> context, Message message, TagResolver... placeholders) {
        messages.send(context.getSource().getSender(), message, placeholders);
    }

    /**
     * Sends a message without the prefix, for the body of a multi-line reply.
     *
     * @param context      the command context
     * @param message      the message
     * @param placeholders the values to substitute
     */
    public void sendPlain(
            CommandContext<CommandSourceStack> context, Message message, TagResolver... placeholders) {
        messages.sendUnprefixed(context.getSource().getSender(), message, placeholders);
    }
}
