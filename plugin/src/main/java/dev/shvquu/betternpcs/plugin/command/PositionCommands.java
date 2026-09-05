package dev.shvquu.betternpcs.plugin.command;

import static dev.shvquu.betternpcs.plugin.command.CommandSupport.FAILURE;
import static dev.shvquu.betternpcs.plugin.command.CommandSupport.SUCCESS;
import static dev.shvquu.betternpcs.plugin.command.CommandSupport.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.core.i18n.Message;
import dev.shvquu.betternpcs.core.i18n.Placeholders;
import dev.shvquu.betternpcs.core.npc.NpcHandle;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Moving NPCs and moving to them.
 *
 * <p>Two commands that are easy to confuse, so they are named for what they move: {@code /npc move}
 * brings the NPC to you, {@code /npc teleport} takes you to the NPC.
 *
 * @since 1.0.0
 */
final class PositionCommands {

    private final CommandSupport support;

    /**
     * Creates the subcommands.
     *
     * @param support shared command plumbing
     * @throws NullPointerException if {@code support} is {@code null}
     */
    PositionCommands(CommandSupport support) {
        this.support = Objects.requireNonNull(support, "support");
    }

    // ---------------------------------------------------------------------------------------------

    LiteralArgumentBuilder<CommandSourceStack> move() {
        return literal("move", Permissions.MOVE)
                .executes(context -> move(context, support.resolveTargeted(context)))
                .then(support.npcArgument()
                        .executes(context -> move(context, support.resolve(context))));
    }

    private int move(CommandContext<CommandSourceStack> context, Optional<NpcHandle> target) {
        Optional<Player> player = support.requirePlayer(context);
        if (target.isEmpty() || player.isEmpty()) {
            return FAILURE;
        }

        NpcHandle npc = target.get();
        Location destination = player.get().getLocation();

        // The NPC faces the way the player was facing, turned around, so it looks at where the
        // player was standing. Placing it facing the same way as the player means an NPC that has
        // its back to whoever just placed it.
        npc.teleport(NpcPosition.of(destination).withRotation(destination.getYaw() + 180.0f, 0.0f));

        support.send(context, Message.NPC_TELEPORTED_TO_YOU, Placeholders.text("npc", npc.name()));
        return SUCCESS;
    }

    // ---------------------------------------------------------------------------------------------

    LiteralArgumentBuilder<CommandSourceStack> teleport() {
        return literal("teleport", Permissions.TELEPORT)
                .then(support.npcArgument().executes(this::teleport));
    }

    private int teleport(CommandContext<CommandSourceStack> context) {
        Optional<NpcHandle> target = support.resolve(context);
        Optional<Player> player = support.requirePlayer(context);
        if (target.isEmpty() || player.isEmpty()) {
            return FAILURE;
        }

        NpcHandle npc = target.get();
        Optional<Location> destination = npc.position().toLocation();
        if (destination.isEmpty()) {
            // The NPC's world is not loaded, so there is nowhere to go.
            support.send(context, Message.UNKNOWN_WORLD,
                    Placeholders.text("world", npc.position().world()));
            return FAILURE;
        }

        player.get().teleport(destination.get());
        support.send(context, Message.NPC_YOU_TELEPORTED, Placeholders.text("npc", npc.name()));
        return SUCCESS;
    }
}
