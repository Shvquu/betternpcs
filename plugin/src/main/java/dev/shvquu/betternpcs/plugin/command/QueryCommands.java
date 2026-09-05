package dev.shvquu.betternpcs.plugin.command;

import static dev.shvquu.betternpcs.plugin.command.CommandSupport.FAILURE;
import static dev.shvquu.betternpcs.plugin.command.CommandSupport.SUCCESS;
import static dev.shvquu.betternpcs.plugin.command.CommandSupport.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.core.i18n.Message;
import dev.shvquu.betternpcs.core.i18n.Placeholders;
import dev.shvquu.betternpcs.core.npc.NpcHandle;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;

/**
 * Listing and inspecting NPCs.
 *
 * @since 1.0.0
 */
final class QueryCommands {

    /** How many NPCs one page of {@code /npc list} holds. */
    private static final int PAGE_SIZE = 10;

    private final CommandSupport support;

    /**
     * Creates the subcommands.
     *
     * @param support shared command plumbing
     * @throws NullPointerException if {@code support} is {@code null}
     */
    QueryCommands(CommandSupport support) {
        this.support = Objects.requireNonNull(support, "support");
    }

    // ---------------------------------------------------------------------------------------------

    LiteralArgumentBuilder<CommandSourceStack> list() {
        return literal("list", Permissions.LIST)
                .executes(context -> list(context, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> list(context, IntegerArgumentType.getInteger(context, "page"))));
    }

    private int list(CommandContext<CommandSourceStack> context, int page) {
        List<NpcHandle> npcs = new ArrayList<>(support.manager().handles());
        if (npcs.isEmpty()) {
            support.send(context, Message.LIST_EMPTY);
            return SUCCESS;
        }
        npcs.sort(Comparator.comparing(NpcHandle::name, String.CASE_INSENSITIVE_ORDER));

        int pages = (npcs.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (page > pages) {
            support.send(context, Message.LIST_PAGE_OUT_OF_RANGE,
                    Placeholders.number("page", page),
                    Placeholders.number("pages", pages));
            return FAILURE;
        }

        support.send(context, Message.LIST_HEADER,
                Placeholders.number("count", npcs.size()),
                Placeholders.number("page", page),
                Placeholders.number("pages", pages));

        int from = (page - 1) * PAGE_SIZE;
        for (NpcHandle npc : npcs.subList(from, Math.min(from + PAGE_SIZE, npcs.size()))) {
            support.sendPlain(context, Message.LIST_ENTRY,
                    Placeholders.text("npc", npc.name()),
                    Placeholders.text("type", npc.type().id()),
                    Placeholders.text("world", npc.position().world()),
                    Placeholders.component("state", state(context, npc)));
        }
        return SUCCESS;
    }

    private Component state(CommandContext<CommandSourceStack> context, NpcHandle npc) {
        return support.messages().render(
                context.getSource().getSender(),
                npc.isSpawned() ? Message.STATE_SPAWNED : Message.STATE_DESPAWNED);
    }

    // ---------------------------------------------------------------------------------------------

    LiteralArgumentBuilder<CommandSourceStack> info() {
        return literal("info", Permissions.INFO)
                .executes(context -> info(context, support.resolveTargeted(context)))
                .then(support.npcArgument()
                        .executes(context -> info(context, support.resolve(context))));
    }

    private int info(CommandContext<CommandSourceStack> context, Optional<NpcHandle> target) {
        if (target.isEmpty()) {
            return FAILURE;
        }
        NpcHandle npc = target.get();

        support.send(context, Message.INFO_HEADER, Placeholders.text("npc", npc.name()));

        line(context, "id", npc.uniqueId().toString());
        line(context, "type", npc.type().id());
        line(context, "state", npc.state().name().toLowerCase(java.util.Locale.ROOT));
        line(context, "position", npc.position().toString());
        line(context, "viewers", String.valueOf(npc.viewers().size()));
        line(context, "display-name", npc.displayName().orElse("-"));
        line(context, "skin", npc.skinSource().map(source ->
                source.kind().name().toLowerCase(java.util.Locale.ROOT) + " " + source.value())
                .orElse("-"));
        line(context, "equipment", npc.equipment().isEmpty()
                ? "-"
                : String.join(", ", npc.equipment().filledSlots().stream().map(Enum::name).toList()));
        line(context, "visibility", npc.visibility().toString());
        line(context, "look", npc.look().mode().name().toLowerCase(java.util.Locale.ROOT));
        line(context, "nametag", npc.nametag().visible()
                ? npc.nametag().text().orElse("<display name>")
                : "hidden");
        line(context, "hologram", npc.hologram().isEmpty()
                ? "-"
                : npc.hologram().lines().size() + " line(s)");

        int actions = npc.actions().values().stream().mapToInt(List::size).sum();
        line(context, "actions", actions == 0 ? "-" : summariseActions(npc));

        if (!npc.metadata().isEmpty()) {
            line(context, "metadata", String.join(", ", npc.metadata().keySet()));
        }
        return SUCCESS;
    }

    private static String summariseActions(NpcHandle npc) {
        List<String> parts = new ArrayList<>();
        for (InteractionType interaction : InteractionType.values()) {
            int count = npc.actions(interaction).size();
            if (count > 0) {
                parts.add(interaction.name().toLowerCase(java.util.Locale.ROOT) + "=" + count);
            }
        }
        return String.join(", ", parts);
    }

    private void line(CommandContext<CommandSourceStack> context, String key, String value) {
        support.sendPlain(context, Message.INFO_LINE,
                Placeholders.text("key", key),
                Placeholders.text("value", value));
    }
}
