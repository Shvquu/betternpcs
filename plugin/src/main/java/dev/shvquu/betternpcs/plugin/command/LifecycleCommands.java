package dev.shvquu.betternpcs.plugin.command;

import static dev.shvquu.betternpcs.plugin.command.CommandSupport.FAILURE;
import static dev.shvquu.betternpcs.plugin.command.CommandSupport.SUCCESS;
import static dev.shvquu.betternpcs.plugin.command.CommandSupport.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.shvquu.betternpcs.api.npc.Npc;
import dev.shvquu.betternpcs.api.npc.NpcType;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.core.i18n.Message;
import dev.shvquu.betternpcs.core.i18n.Placeholders;
import dev.shvquu.betternpcs.core.npc.DefaultNpcManager;
import dev.shvquu.betternpcs.core.npc.NpcHandle;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Creating, deleting, spawning, despawning, saving and reloading.
 *
 * @since 1.0.0
 */
final class LifecycleCommands {

    private final CommandSupport support;
    private final Runnable reloadPlugin;

    /**
     * Creates the subcommands.
     *
     * @param support      shared command plumbing
     * @param reloadPlugin reloads the configuration and languages; supplied by the plugin, because
     *                     reloading is the one thing a command needs that is not the engine's job
     * @throws NullPointerException if either argument is {@code null}
     */
    LifecycleCommands(CommandSupport support, Runnable reloadPlugin) {
        this.support = Objects.requireNonNull(support, "support");
        this.reloadPlugin = Objects.requireNonNull(reloadPlugin, "reloadPlugin");
    }

    // ---------------------------------------------------------------------------------------------

    LiteralArgumentBuilder<CommandSourceStack> create() {
        return literal("create", Permissions.CREATE)
                .then(Commands.argument("name", StringArgumentType.word())
                        // No type given means a player NPC, which is what nearly every NPC is.
                        .executes(context -> create(context, NpcType.PLAYER))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(CommandSupport.suggesting(livingTypeNames()))
                                .executes(this::createTyped)));
    }

    private int createTyped(CommandContext<CommandSourceStack> context) {
        String typeName = StringArgumentType.getString(context, "type");
        NpcType type;
        try {
            type = NpcType.fromId(typeName);
        } catch (IllegalArgumentException unknown) {
            support.send(context, Message.NPC_TYPE_UNKNOWN, Placeholders.text("input", typeName));
            return FAILURE;
        }
        return create(context, type);
    }

    private int create(CommandContext<CommandSourceStack> context, NpcType type) {
        Optional<Player> player = support.requirePlayer(context);
        if (player.isEmpty()) {
            return FAILURE;
        }

        String name = StringArgumentType.getString(context, "name");
        NpcPosition position = NpcPosition.of(player.get().getLocation());

        Npc npc;
        try {
            npc = support.manager().create(name, type, position, context.getSource().getSender());
        } catch (IllegalArgumentException rejected) {
            // Two different reasons land here, and a server owner deserves to be told which.
            Message reason = support.manager().exists(name)
                    ? Message.NPC_NAME_TAKEN
                    : Message.NPC_NAME_INVALID;
            support.send(context, reason, Placeholders.text("npc", name));
            return FAILURE;
        } catch (IllegalStateException cancelled) {
            support.send(context, Message.NPC_SPAWN_CANCELLED, Placeholders.text("npc", name));
            return FAILURE;
        }

        npc.spawn();
        support.send(context, Message.NPC_CREATED,
                Placeholders.text("npc", npc.name()),
                Placeholders.text("type", type.id()));
        return SUCCESS;
    }

    private static List<String> livingTypeNames() {
        return Arrays.stream(EntityType.values())
                .filter(type -> type.getEntityClass() != null
                        && LivingEntity.class.isAssignableFrom(type.getEntityClass()))
                .map(Enum::name)
                .sorted()
                .toList();
    }

    // ---------------------------------------------------------------------------------------------

    LiteralArgumentBuilder<CommandSourceStack> remove() {
        return literal("remove", Permissions.REMOVE)
                .executes(context -> remove(context, support.resolveTargeted(context)))
                .then(support.npcArgument()
                        .executes(context -> remove(context, support.resolve(context))));
    }

    private int remove(CommandContext<CommandSourceStack> context, Optional<NpcHandle> target) {
        if (target.isEmpty()) {
            return FAILURE;
        }
        NpcHandle npc = target.get();
        String name = npc.name();

        support.manager().delete(npc, context.getSource().getSender()).thenAccept(deleted ->
                support.messages().send(
                        context.getSource().getSender(),
                        deleted ? Message.NPC_DELETED : Message.NPC_DELETE_CANCELLED,
                        Placeholders.text("npc", name)));
        return SUCCESS;
    }

    // ---------------------------------------------------------------------------------------------

    LiteralArgumentBuilder<CommandSourceStack> spawn() {
        return literal("spawn", Permissions.SPAWN)
                .executes(context -> spawn(context, support.resolveTargeted(context)))
                .then(support.npcArgument()
                        .executes(context -> spawn(context, support.resolve(context))));
    }

    private int spawn(CommandContext<CommandSourceStack> context, Optional<NpcHandle> target) {
        if (target.isEmpty()) {
            return FAILURE;
        }
        NpcHandle npc = target.get();

        if (npc.isSpawned()) {
            support.send(context, Message.NPC_ALREADY_SPAWNED, Placeholders.text("npc", npc.name()));
            return FAILURE;
        }
        if (!npc.spawn()) {
            support.send(context, Message.NPC_SPAWN_CANCELLED, Placeholders.text("npc", npc.name()));
            return FAILURE;
        }
        support.send(context, Message.NPC_SPAWNED, Placeholders.text("npc", npc.name()));
        return SUCCESS;
    }

    LiteralArgumentBuilder<CommandSourceStack> despawn() {
        return literal("despawn", Permissions.SPAWN)
                .executes(context -> despawn(context, support.resolveTargeted(context)))
                .then(support.npcArgument()
                        .executes(context -> despawn(context, support.resolve(context))));
    }

    private int despawn(CommandContext<CommandSourceStack> context, Optional<NpcHandle> target) {
        if (target.isEmpty()) {
            return FAILURE;
        }
        NpcHandle npc = target.get();

        if (!npc.despawn()) {
            support.send(context, Message.NPC_ALREADY_DESPAWNED, Placeholders.text("npc", npc.name()));
            return FAILURE;
        }
        support.send(context, Message.NPC_DESPAWNED, Placeholders.text("npc", npc.name()));
        return SUCCESS;
    }

    // ---------------------------------------------------------------------------------------------

    LiteralArgumentBuilder<CommandSourceStack> save() {
        return literal("save", Permissions.SAVE).executes(this::save);
    }

    private int save(CommandContext<CommandSourceStack> context) {
        support.manager().saveAll().whenComplete((count, failure) -> {
            if (failure != null) {
                support.messages().send(context.getSource().getSender(), Message.STORAGE_ERROR);
                return;
            }
            support.messages().send(context.getSource().getSender(), Message.NPC_SAVED_ALL,
                    Placeholders.number("count", count));
        });
        return SUCCESS;
    }

    // ---------------------------------------------------------------------------------------------

    LiteralArgumentBuilder<CommandSourceStack> reload() {
        return literal("reload", Permissions.RELOAD).executes(this::reload);
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        long startedAt = System.nanoTime();

        try {
            reloadPlugin.run();
        } catch (RuntimeException failure) {
            // A bad edit to config.yml is the overwhelmingly likely cause, and the message from a
            // ConfigurationException names the exact path.
            support.send(context, Message.RELOAD_FAILED,
                    Placeholders.text("error", String.valueOf(failure.getMessage())));
            return FAILURE;
        }

        DefaultNpcManager manager = support.manager();
        manager.reload().whenComplete((count, failure) -> {
            if (failure != null) {
                support.messages().send(context.getSource().getSender(), Message.RELOAD_FAILED,
                        Placeholders.text("error", String.valueOf(failure.getMessage())));
                return;
            }
            // Back onto the main thread before spawning: the future may complete on the storage
            // executor, and spawning touches the registry and the spatial index.
            support.onMain(() -> {
                manager.spawnAll();
                long millis = (System.nanoTime() - startedAt) / 1_000_000L;
                support.messages().send(context.getSource().getSender(), Message.RELOAD_SUCCESS,
                        Placeholders.number("count", count),
                        Placeholders.number("millis", millis));
            });
        });
        return SUCCESS;
    }
}
