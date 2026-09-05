package dev.shvquu.betternpcs.plugin;

import dev.shvquu.betternpcs.api.event.NpcDamageEvent;
import dev.shvquu.betternpcs.api.event.NpcInteractEvent;
import dev.shvquu.betternpcs.api.animation.NpcAnimation;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.core.config.BetterNpcsConfig;
import dev.shvquu.betternpcs.core.npc.EngineServices;
import dev.shvquu.betternpcs.core.npc.NpcHandle;
import dev.shvquu.betternpcs.core.render.NpcInteractionSink;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

/**
 * Turns a raw interaction packet into an event and an action chain.
 *
 * <h2>What happens on which thread</h2>
 *
 * <p>{@link #onInteract} is called from the network thread, for every entity every player interacts
 * with — including all the real ones this plugin knows nothing about. Two checks therefore happen
 * there, before anything is scheduled: is this entity id one of ours, and has this player clicked
 * this NPC too recently. Both are map lookups. Everything after that is scheduled onto the main
 * thread.
 *
 * <p>Doing the cooldown check off the main thread is what stops a held mouse button from queueing a
 * task per click. Clients send one interact packet per click, and a player leaning on the button
 * produces a steady stream of them.
 *
 * @since 1.0.0
 */
final class NpcInteractionHandler implements NpcInteractionSink {

    /** The damage the engine reports for an attack it has no better figure for. */
    private static final double NOMINAL_ATTACK_DAMAGE = 1.0;

    private final EngineServices services;
    private final Supplier<BetterNpcsConfig> config;
    private final Map<String, Long> lastInteraction = new ConcurrentHashMap<>();

    /**
     * Creates the handler.
     *
     * @param services the engine's collaborators
     * @param config   supplies the current configuration
     * @throws NullPointerException if either argument is {@code null}
     */
    NpcInteractionHandler(EngineServices services, Supplier<BetterNpcsConfig> config) {
        this.services = Objects.requireNonNull(services, "services");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public void onInteract(
            Player player,
            int entityId,
            InteractionType interaction,
            EquipmentSlot hand,
            Vector clickedAt) {

        NpcHandle npc = services.registry().byEntityId(entityId).orElse(null);
        if (npc == null) {
            // A real entity, or one of our text display lines. Both are the common case.
            return;
        }
        if (!accept(player, npc.uniqueId())) {
            return;
        }

        services.scheduler().runOnMainThread(
                () -> handle(npc, player, interaction, hand, clickedAt));
    }

    /**
     * Returns whether this interaction is far enough from the previous one to act on.
     *
     * @param player   who interacted
     * @param npcId    which NPC they interacted with
     * @return {@code true} if the interaction should be handled
     */
    private boolean accept(Player player, UUID npcId) {
        int cooldownMillis = config.get().npc().interactionCooldown();
        if (cooldownMillis <= 0) {
            return true;
        }

        String key = player.getUniqueId() + ":" + npcId;
        long now = System.nanoTime() / 1_000_000L;

        // compute rather than get-then-put: the check and the update have to be one atomic step.
        // Packets from one player are decoded on one netty thread today, but nothing in the protocol
        // promises that, and a check-then-act race here would let a burst of clicks through the
        // cooldown that exists precisely to stop bursts of clicks.
        AtomicBoolean accepted = new AtomicBoolean();
        lastInteraction.compute(key, (ignored, previous) -> {
            if (previous != null && now - previous < cooldownMillis) {
                return previous;
            }
            accepted.set(true);
            return now;
        });
        return accepted.get();
    }

    private void handle(
            NpcHandle npc,
            Player player,
            InteractionType interaction,
            EquipmentSlot hand,
            Vector clickedAt) {

        if (npc.isRemoved() || !npc.isSpawned() || !player.isOnline()) {
            // Any of the three can become true between the packet arriving and this running.
            return;
        }

        NpcInteractEvent event =
                services.events().fire(new NpcInteractEvent(npc, player, interaction, hand, clickedAt));
        if (event.isCancelled()) {
            return;
        }

        if (interaction.isLeftClick() && !npc.appearance().invulnerable()) {
            handleAttack(npc, player);
        }

        runActions(npc, player, interaction);
    }

    private void handleAttack(NpcHandle npc, Player player) {
        NpcDamageEvent damage =
                services.events().fire(new NpcDamageEvent(npc, player, NOMINAL_ATTACK_DAMAGE));
        if (!damage.isCancelled()) {
            // BetterNPCs does not model NPC health; the hurt animation is the whole of its own
            // reaction, and a combat plugin that cancelled the event gets to suppress even that.
            npc.playAnimation(NpcAnimation.TAKE_DAMAGE, player);
        }
    }

    private void runActions(NpcHandle npc, Player player, InteractionType interaction) {
        if (!npc.actions(interaction).isEmpty()) {
            npc.runActions(player, interaction);
            return;
        }

        // Nothing bound to the sneaking variant, so a plain binding takes it. That is what a server
        // owner expects from a bare `right-click:` entry in a configuration file — otherwise their
        // shop NPC stops working the moment a player happens to be crouching.
        InteractionType relaxed = interaction.withoutSneaking();
        if (relaxed != interaction && !npc.actions(relaxed).isEmpty()) {
            npc.runActions(player, relaxed);
        }
    }

    /**
     * Forgets a player's cooldowns.
     *
     * <p>Called when they quit, so the map does not grow by one entry per NPC per player for the
     * lifetime of the server.
     *
     * @param player the player who left
     */
    void forget(Player player) {
        String prefix = player.getUniqueId() + ":";
        lastInteraction.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
