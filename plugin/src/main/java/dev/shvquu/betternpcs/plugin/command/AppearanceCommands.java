package dev.shvquu.betternpcs.plugin.command;

import static dev.shvquu.betternpcs.plugin.command.CommandSupport.FAILURE;
import static dev.shvquu.betternpcs.plugin.command.CommandSupport.SUCCESS;
import static dev.shvquu.betternpcs.plugin.command.CommandSupport.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.shvquu.betternpcs.api.npc.NpcType;
import dev.shvquu.betternpcs.api.skin.SkinSource;
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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Changing how an NPC looks: its name, type, skin and equipment.
 *
 * @since 1.0.0
 */
final class AppearanceCommands {

    private final CommandSupport support;

    /**
     * Creates the subcommands.
     *
     * @param support shared command plumbing
     * @throws NullPointerException if {@code support} is {@code null}
     */
    AppearanceCommands(CommandSupport support) {
        this.support = Objects.requireNonNull(support, "support");
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * {@code /npc name <npc> [text...]} — sets the display name, or clears it when no text is given.
     *
     * @return the builder
     */
    LiteralArgumentBuilder<CommandSourceStack> displayName() {
        return literal("name", Permissions.EDIT)
                .then(support.npcArgument()
                        // No text at all clears the name. A `clear` literal would be ambiguous with
                        // a display name that is actually the word "clear".
                        .executes(context -> setDisplayName(context, null))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(context -> setDisplayName(
                                        context, StringArgumentType.getString(context, "text")))));
    }

    private int setDisplayName(CommandContext<CommandSourceStack> context, String text) {
        Optional<NpcHandle> target = support.resolve(context);
        if (target.isEmpty()) {
            return FAILURE;
        }
        NpcHandle npc = target.get();
        npc.setDisplayName(text);

        if (text == null) {
            support.send(context, Message.NPC_DISPLAY_NAME_CLEARED,
                    Placeholders.text("npc", npc.name()));
        } else {
            support.send(context, Message.NPC_DISPLAY_NAME_SET,
                    Placeholders.text("npc", npc.name()),
                    // Rendered as a component so the administrator sees the formatting they typed
                    // rather than the MiniMessage source they typed it in.
                    Placeholders.component("name", support.messages().renderRaw(
                            context.getSource().getSender(), text)));
        }
        return SUCCESS;
    }

    // ---------------------------------------------------------------------------------------------

    LiteralArgumentBuilder<CommandSourceStack> rename() {
        return literal("rename", Permissions.EDIT)
                .then(support.npcArgument()
                        .then(Commands.argument("new", StringArgumentType.word())
                                .executes(this::rename)));
    }

    private int rename(CommandContext<CommandSourceStack> context) {
        Optional<NpcHandle> target = support.resolve(context);
        if (target.isEmpty()) {
            return FAILURE;
        }
        NpcHandle npc = target.get();
        String oldName = npc.name();
        String newName = StringArgumentType.getString(context, "new");

        try {
            npc.rename(newName);
        } catch (IllegalArgumentException rejected) {
            Message reason = support.manager().exists(newName)
                    ? Message.NPC_NAME_TAKEN
                    : Message.NPC_NAME_INVALID;
            support.send(context, reason, Placeholders.text("npc", newName));
            return FAILURE;
        }

        support.send(context, Message.NPC_RENAMED,
                Placeholders.text("old", oldName),
                Placeholders.text("new", newName));
        return SUCCESS;
    }

    // ---------------------------------------------------------------------------------------------

    LiteralArgumentBuilder<CommandSourceStack> type() {
        return literal("type", Permissions.EDIT)
                .then(support.npcArgument()
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(CommandSupport.suggesting(livingTypeNames()))
                                .executes(this::setType)));
    }

    private int setType(CommandContext<CommandSourceStack> context) {
        Optional<NpcHandle> target = support.resolve(context);
        if (target.isEmpty()) {
            return FAILURE;
        }
        String typeName = StringArgumentType.getString(context, "type");

        NpcType type;
        try {
            type = NpcType.fromId(typeName);
        } catch (IllegalArgumentException unknown) {
            support.send(context, Message.NPC_TYPE_UNKNOWN, Placeholders.text("input", typeName));
            return FAILURE;
        }

        NpcHandle npc = target.get();
        npc.setType(type);
        support.send(context, Message.NPC_TYPE_SET,
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

    LiteralArgumentBuilder<CommandSourceStack> skin() {
        return literal("skin", Permissions.EDIT)
                .then(support.npcArgument()
                        .then(Commands.literal("clear").executes(this::clearSkin))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(this::setSkin)));
    }

    private int clearSkin(CommandContext<CommandSourceStack> context) {
        Optional<NpcHandle> target = support.resolve(context);
        if (target.isEmpty()) {
            return FAILURE;
        }
        NpcHandle npc = target.get();
        npc.setSkin((SkinSource) null);
        support.send(context, Message.SKIN_CLEARED, Placeholders.text("npc", npc.name()));
        return SUCCESS;
    }

    private int setSkin(CommandContext<CommandSourceStack> context) {
        Optional<NpcHandle> target = support.resolve(context);
        if (target.isEmpty()) {
            return FAILURE;
        }
        NpcHandle npc = target.get();
        if (!npc.type().isPlayer()) {
            support.send(context, Message.SKIN_ONLY_PLAYER_NPCS, Placeholders.text("npc", npc.name()));
            return FAILURE;
        }

        String skinName = StringArgumentType.getString(context, "player");
        SkinSource source;
        try {
            source = SkinSource.playerName(skinName);
        } catch (IllegalArgumentException impossible) {
            support.send(context, Message.SKIN_NOT_FOUND, Placeholders.text("skin", skinName));
            return FAILURE;
        }

        support.send(context, Message.SKIN_LOADING,
                Placeholders.text("npc", npc.name()),
                Placeholders.text("skin", skinName));

        // The lookup is a network call. The command returns immediately and the result is reported
        // when it arrives, which is the only way this can work without stalling the server.
        npc.setSkin(source).whenComplete((ignored, failure) -> {
            if (failure != null) {
                support.messages().send(context.getSource().getSender(), Message.SKIN_FAILED,
                        Placeholders.text("skin", skinName),
                        Placeholders.text("error", String.valueOf(failure.getMessage())));
                return;
            }
            if (npc.skin().isEmpty()) {
                support.messages().send(context.getSource().getSender(), Message.SKIN_NOT_FOUND,
                        Placeholders.text("skin", skinName));
                return;
            }
            support.messages().send(context.getSource().getSender(), Message.SKIN_SET,
                    Placeholders.text("npc", npc.name()),
                    Placeholders.text("skin", skinName));
        });
        return SUCCESS;
    }

    // ---------------------------------------------------------------------------------------------

    LiteralArgumentBuilder<CommandSourceStack> equipment() {
        return literal("equipment", Permissions.EDIT)
                .then(support.npcArgument()
                        .then(Commands.argument("slot", StringArgumentType.word())
                                .suggests(CommandSupport.suggesting(slotNames()))
                                // With no third argument the held item is used, which is the whole
                                // point: there is no sane way to type an item with its components.
                                .executes(context -> setEquipment(context, false))
                                .then(Commands.literal("clear")
                                        .executes(context -> setEquipment(context, true)))));
    }

    private int setEquipment(CommandContext<CommandSourceStack> context, boolean clear) {
        Optional<NpcHandle> target = support.resolve(context);
        if (target.isEmpty()) {
            return FAILURE;
        }
        NpcHandle npc = target.get();

        String slotName = StringArgumentType.getString(context, "slot");
        EquipmentSlot slot;
        try {
            slot = EquipmentSlot.valueOf(slotName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            support.send(context, Message.EQUIPMENT_SLOT_UNKNOWN, Placeholders.text("input", slotName));
            return FAILURE;
        }

        if (clear) {
            npc.setEquipment(slot, null);
            support.send(context, Message.EQUIPMENT_CLEARED,
                    Placeholders.text("npc", npc.name()),
                    Placeholders.text("slot", slot.name()));
            return SUCCESS;
        }

        Optional<Player> player = support.requirePlayer(context);
        if (player.isEmpty()) {
            return FAILURE;
        }

        ItemStack held = player.get().getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            support.send(context, Message.EQUIPMENT_HOLD_ITEM);
            return FAILURE;
        }

        npc.setEquipment(slot, held);
        support.send(context, Message.EQUIPMENT_SET,
                Placeholders.text("npc", npc.name()),
                Placeholders.text("slot", slot.name()),
                Placeholders.text("item", held.getType().name()));
        return SUCCESS;
    }

    private static List<String> slotNames() {
        return Arrays.stream(EquipmentSlot.values()).map(Enum::name).sorted().toList();
    }
}
