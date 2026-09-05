package dev.shvquu.betternpcs.core.npc;

import dev.shvquu.betternpcs.api.npc.Npc;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.core.i18n.Placeholders;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

/**
 * The placeholders BetterNPCs resolves itself, without PlaceholderAPI.
 *
 * <p>Deliberately a small, fixed set covering what an NPC's own text and actions need. Anything
 * beyond it is PlaceholderAPI's job, and duplicating its catalogue here would mean two answers to
 * the same question.
 *
 * <p>Available in nametags, hologram lines and action arguments:
 *
 * <ul>
 *   <li>{@code <player_name>}, {@code <player_uuid>} — the viewing or interacting player</li>
 *   <li>{@code <npc_name>}, {@code <npc_id>} — the NPC</li>
 *   <li>{@code <npc_world>}, {@code <npc_x>}, {@code <npc_y>}, {@code <npc_z>} — where it stands</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class NpcPlaceholders {

    private NpcPlaceholders() {
        throw new AssertionError("No instances");
    }

    /**
     * Returns MiniMessage resolvers for an NPC and the player looking at it.
     *
     * <p>Every value is inserted as literal text, so a player name that looks like a tag stays a
     * player name.
     *
     * @param npc    the NPC, may be {@code null} when rendering text that has no NPC context
     * @param player the player, may be {@code null} for the console
     * @return a resolver covering every built-in placeholder
     */
    public static TagResolver tags(Npc npc, Player player) {
        Map<String, String> values = values(npc, player);
        TagResolver.Builder builder = TagResolver.builder();
        values.forEach((name, value) -> builder.resolver(Placeholders.text(name, value)));
        return builder.build();
    }

    /**
     * Expands built-in placeholders in plain text, without involving MiniMessage.
     *
     * <p>Used for action arguments that are not messages — a command line, a sound key, a set of
     * coordinates — where MiniMessage has no business running at all.
     *
     * <h2>A caveat worth knowing</h2>
     *
     * <p>A value substituted into a command line becomes part of that command line. None of the
     * built-in placeholders can contain a space, so none of them can introduce an argument; that is
     * why this set is fixed and small rather than open. A PlaceholderAPI value is not covered by
     * that guarantee, which is why adding a {@code console:} action is gated behind its own
     * permission.
     *
     * @param text   the text to expand
     * @param npc    the NPC, may be {@code null}
     * @param player the player, may be {@code null}
     * @return the expanded text
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public static String expand(String text, Npc npc, Player player) {
        Objects.requireNonNull(text, "text");
        if (text.indexOf('<') < 0) {
            return text;
        }
        String expanded = text;
        for (Map.Entry<String, String> value : values(npc, player).entrySet()) {
            expanded = expanded.replace("<" + value.getKey() + ">", value.getValue());
        }
        return expanded;
    }

    private static Map<String, String> values(Npc npc, Player player) {
        Map<String, String> values = new LinkedHashMap<>();

        values.put("player_name", player == null ? "" : player.getName());
        values.put("player_uuid", player == null ? "" : player.getUniqueId().toString());

        if (npc == null) {
            values.put("npc_name", "");
            values.put("npc_id", "");
            values.put("npc_world", "");
            values.put("npc_x", "");
            values.put("npc_y", "");
            values.put("npc_z", "");
            return values;
        }

        values.put("npc_name", npc.name());
        values.put("npc_id", npc.uniqueId().toString());

        NpcPosition position = npc.position();
        values.put("npc_world", position.world());
        values.put("npc_x", coordinate(position.x()));
        values.put("npc_y", coordinate(position.y()));
        values.put("npc_z", coordinate(position.z()));
        return values;
    }

    private static String coordinate(double value) {
        // Locale.ROOT so that a server running with a German locale does not substitute "100,5" into
        // a command, where the comma would split it into two arguments.
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
