package dev.shvquu.betternpcs.api.action;

import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.api.npc.Npc;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Everything a {@link NpcActionHandler} needs to carry out one action.
 *
 * <p>Handlers receive a context rather than loose arguments so that the API can grow — a new piece
 * of information becomes a new method with a default, not a signature change that breaks every
 * extension.
 *
 * <p>A context is valid only for the duration of the {@link NpcActionHandler#execute(ActionContext)}
 * call that received it. Storing one and using it later is unsupported: the player may have
 * disconnected and the NPC may have been removed.
 *
 * <p>Handlers are invoked on the main server thread.
 *
 * @since 1.0.0
 */
public interface ActionContext {

    /**
     * Returns the NPC that was interacted with.
     *
     * @return the NPC
     */
    Npc npc();

    /**
     * Returns the player who interacted.
     *
     * @return the player, guaranteed to be online for the duration of the call
     */
    Player player();

    /**
     * Returns what the player did.
     *
     * @return the interaction type
     */
    InteractionType interaction();

    /**
     * Returns the action being executed.
     *
     * @return the definition, whose {@link ActionDefinition#argument()} is what the handler acts on
     */
    ActionDefinition definition();

    /**
     * Returns the raw, unresolved argument.
     *
     * <p>Shorthand for {@code definition().argument()}. Most handlers want {@link #resolve(String)}
     * applied to this first.
     *
     * @return the argument, never {@code null} but possibly empty
     */
    default String argument() {
        return definition().argument();
    }

    /**
     * Expands placeholders in a string for this player and NPC.
     *
     * <p>Resolves the built-in NPC placeholders and, when PlaceholderAPI is installed, its
     * placeholders too. When it is not, PlaceholderAPI's tokens are left untouched rather than
     * blanked, so that installing it later fixes the text instead of a missing plugin silently
     * eating it.
     *
     * @param text the text to expand
     * @return the expanded text
     * @throws NullPointerException if {@code text} is {@code null}
     */
    String resolve(String text);

    /**
     * Expands placeholders and then parses the result as MiniMessage.
     *
     * <p>Placeholder values are inserted as literal text, not re-parsed as MiniMessage, so a player
     * whose name contains something tag-shaped cannot inject formatting or a click event into a
     * message meant for someone else.
     *
     * @param miniMessage the MiniMessage source
     * @return the rendered component
     * @throws NullPointerException if {@code miniMessage} is {@code null}
     */
    Component render(String miniMessage);

    /**
     * Stops the remaining actions in this chain from running.
     *
     * <p>How a conditional action — a permission check, a cooldown, a quest requirement — aborts
     * everything configured after it.
     */
    void stopChain();

    /**
     * Returns whether {@link #stopChain()} has been called.
     *
     * @return {@code true} if the remaining actions will be skipped
     */
    boolean isChainStopped();

    /**
     * Returns a value another handler earlier in this chain stored.
     *
     * <p>Scoped to one execution of one action chain. Lets a pair of cooperating actions from the
     * same extension pass state without a static map keyed by player.
     *
     * @param key the key
     * @param <T> the value type, unchecked — the caller is responsible for using a consistent type
     *            for a given key
     * @return the value, or empty if nothing was stored under {@code key}
     * @throws NullPointerException if {@code key} is {@code null}
     */
    <T> Optional<T> attribute(String key);

    /**
     * Stores a value for later handlers in this chain.
     *
     * @param key   the key
     * @param value the value, or {@code null} to remove the entry
     * @throws NullPointerException if {@code key} is {@code null}
     */
    void attribute(String key, Object value);
}
