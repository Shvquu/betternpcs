package dev.shvquu.betternpcs.core.action;

import dev.shvquu.betternpcs.api.action.ActionDefinition;
import dev.shvquu.betternpcs.api.action.ActionRegistry;
import dev.shvquu.betternpcs.api.action.NpcActionHandler;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.api.npc.Npc;
import dev.shvquu.betternpcs.core.i18n.MessageService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * Runs an NPC's action chain for one interaction.
 *
 * <p>Two decisions here shape how NPCs behave when something goes wrong, and both are deliberate.
 *
 * <p>An action that throws is logged with its NPC, player and definition, and the chain
 * <em>continues</em>. A shop NPC whose third action fails should still have run the first two; the
 * alternative leaves a player with a half-applied purchase and no message. A handler that wants a
 * failure to stop everything says so with {@link
 * dev.shvquu.betternpcs.api.action.ActionContext#stopChain()}.
 *
 * <p>An action whose type no handler serves is logged once per execution and skipped, rather than
 * refusing to run the chain. That is the state an NPC is in after an extension is removed, and
 * punishing every player who clicks it would not bring the extension back.
 *
 * @since 1.0.0
 */
public final class ActionExecutor {

    private final ActionRegistry registry;
    private final MessageService messages;
    private final Logger logger;

    /**
     * Creates the executor.
     *
     * @param registry where handlers are looked up
     * @param messages used by the context to expand placeholders
     * @param logger   where failures are reported
     * @throws NullPointerException if any argument is {@code null}
     */
    public ActionExecutor(ActionRegistry registry, MessageService messages, Logger logger) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Runs a chain of actions in order.
     *
     * <p>Called on the main server thread.
     *
     * @param npc         the NPC whose actions these are
     * @param player      the player to run them for
     * @param interaction what the player did
     * @param actions     the actions, in execution order
     * @return the number of actions that ran, which is fewer than {@code actions.size()} when a
     *         handler stopped the chain
     * @throws NullPointerException if any argument is {@code null}
     */
    public int execute(
            Npc npc, Player player, InteractionType interaction, List<ActionDefinition> actions) {
        Objects.requireNonNull(npc, "npc");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(interaction, "interaction");
        Objects.requireNonNull(actions, "actions");

        if (actions.isEmpty()) {
            return 0;
        }

        DefaultActionContext context = new DefaultActionContext(npc, player, interaction, messages);
        int executed = 0;

        for (ActionDefinition definition : actions) {
            Optional<NpcActionHandler> handler = registry.find(definition.type());
            if (handler.isEmpty()) {
                logger.warning(() -> "NPC '" + npc.name() + "' uses the action '" + definition.type()
                        + "', which no plugin provides. Skipping it.");
                continue;
            }

            context.definition(definition);
            try {
                handler.get().execute(context);
                executed++;
            } catch (RuntimeException failure) {
                // Named in full: without the NPC and the action, a server owner has a stack trace
                // from inside a handler and no idea which of their NPCs produced it.
                logger.log(Level.WARNING, failure, () ->
                        "The action '" + definition.serialize() + "' on NPC '" + npc.name()
                                + "' failed for " + player.getName() + ".");
            }

            if (context.isChainStopped()) {
                break;
            }
        }
        return executed;
    }
}
