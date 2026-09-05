package dev.shvquu.betternpcs.core.action;

import dev.shvquu.betternpcs.api.action.ActionContext;
import dev.shvquu.betternpcs.api.action.ActionDefinition;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.api.npc.Npc;
import dev.shvquu.betternpcs.core.i18n.MessageService;
import dev.shvquu.betternpcs.core.npc.NpcPlaceholders;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * The {@link ActionContext} handed to each handler in one action chain.
 *
 * <p>One instance is reused for the whole chain, with {@link #definition(ActionDefinition)} advancing
 * it between steps. That is what makes {@link #attribute(String, Object)} a chain-scoped scratchpad
 * rather than something a handler would otherwise have to build out of a static map keyed by player.
 *
 * <p>Not thread safe, and not meant to be: a chain runs to completion on the main thread.
 *
 * @since 1.0.0
 */
public final class DefaultActionContext implements ActionContext {

    private final Npc npc;
    private final Player player;
    private final InteractionType interaction;
    private final MessageService messages;
    private final Map<String, Object> attributes = new HashMap<>(4);

    private ActionDefinition definition;
    private boolean chainStopped;

    /**
     * Creates the context.
     *
     * @param npc         the NPC that was interacted with
     * @param player      the player who interacted
     * @param interaction what the player did
     * @param messages    used to expand placeholders and render MiniMessage
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultActionContext(
            Npc npc, Player player, InteractionType interaction, MessageService messages) {
        this.npc = Objects.requireNonNull(npc, "npc");
        this.player = Objects.requireNonNull(player, "player");
        this.interaction = Objects.requireNonNull(interaction, "interaction");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Advances the context to the next action in the chain.
     *
     * @param next the action about to run
     * @throws NullPointerException if {@code next} is {@code null}
     */
    public void definition(ActionDefinition next) {
        this.definition = Objects.requireNonNull(next, "definition");
    }

    @Override
    public Npc npc() {
        return npc;
    }

    @Override
    public Player player() {
        return player;
    }

    @Override
    public InteractionType interaction() {
        return interaction;
    }

    @Override
    public ActionDefinition definition() {
        if (definition == null) {
            throw new IllegalStateException("No action is currently executing");
        }
        return definition;
    }

    @Override
    public String resolve(String text) {
        Objects.requireNonNull(text, "text");
        // Built-in placeholders first, then external ones. The order is arbitrary but has to be
        // fixed, and this way a PlaceholderAPI value can never contain something that then gets
        // read as a built-in placeholder.
        return messages.expandPlaceholders(player, NpcPlaceholders.expand(text, npc, player));
    }

    @Override
    public Component render(String miniMessage) {
        Objects.requireNonNull(miniMessage, "miniMessage");
        return messages.renderRaw(player, miniMessage, NpcPlaceholders.tags(npc, player));
    }

    @Override
    public void stopChain() {
        this.chainStopped = true;
    }

    @Override
    public boolean isChainStopped() {
        return chainStopped;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> attribute(String key) {
        Objects.requireNonNull(key, "key");
        // Unchecked by design, and documented as such on the API: the alternative is a typed key
        // object, which would be heavier than the feature deserves.
        return Optional.ofNullable((T) attributes.get(key));
    }

    @Override
    public void attribute(String key, Object value) {
        Objects.requireNonNull(key, "key");
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }
}
