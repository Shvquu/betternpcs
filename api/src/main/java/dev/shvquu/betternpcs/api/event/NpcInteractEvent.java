package dev.shvquu.betternpcs.api.event;

import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.api.npc.Npc;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

/**
 * Fired when a player clicks an NPC, before the NPC's configured actions run.
 *
 * <p>This one event covers every interaction — left click, right click and their sneaking variants —
 * with {@link #getInteraction()} saying which. Attacks arrive here as
 * {@link InteractionType#LEFT_CLICK}; a separate {@link NpcDamageEvent} follows for NPCs that are
 * not invulnerable, for listeners that care about damage specifically rather than about clicks.
 *
 * <p>Cancelling stops the configured actions from running. It does not stop other listeners, which
 * should check {@link #isCancelled()} themselves if they want to respect an earlier veto.
 *
 * <p>Clients send an interaction packet per click, so a player holding down a mouse button produces
 * a stream of these. The engine applies its configured anti-spam interval before firing, but a
 * listener doing anything expensive should still keep that in mind.
 *
 * @since 1.0.0
 */
public class NpcInteractEvent extends NpcEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final InteractionType interaction;
    private final EquipmentSlot hand;
    private final Vector clickedPosition;
    private boolean cancelled;

    /**
     * Creates the event.
     *
     * @param npc             the NPC that was clicked
     * @param player          the player who clicked it
     * @param interaction     what the player did
     * @param hand            the hand used, or {@code null} if the protocol did not say — an attack
     *                        never reports one
     * @param clickedPosition where on the hit box the click landed relative to the NPC's feet, or
     *                        {@code null} if the protocol did not say
     * @throws NullPointerException if {@code npc}, {@code player} or {@code interaction} is
     *                              {@code null}
     */
    public NpcInteractEvent(
            Npc npc,
            Player player,
            InteractionType interaction,
            EquipmentSlot hand,
            Vector clickedPosition) {
        super(npc);
        this.player = Objects.requireNonNull(player, "player");
        this.interaction = Objects.requireNonNull(interaction, "interaction");
        this.hand = hand;
        this.clickedPosition = clickedPosition == null ? null : clickedPosition.clone();
    }

    /**
     * Returns the player who interacted.
     *
     * @return the player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Returns what the player did.
     *
     * @return the interaction type
     */
    public InteractionType getInteraction() {
        return interaction;
    }

    /**
     * Returns which hand the player used.
     *
     * @return {@link EquipmentSlot#HAND} or {@link EquipmentSlot#OFF_HAND}, or empty for an attack,
     *         which the protocol does not attribute to a hand
     */
    public Optional<EquipmentSlot> getHand() {
        return Optional.ofNullable(hand);
    }

    /**
     * Returns where on the NPC the click landed, relative to its feet.
     *
     * <p>Only the interact-at form of the packet carries this, so a plain interact and an attack do
     * not have it. Enough to tell a click on the head from one on the legs, which is how a plugin
     * builds a hit-box-aware NPC.
     *
     * @return a copy of the offset, or empty if the protocol did not report one
     */
    public Optional<Vector> getClickedPosition() {
        return Optional.ofNullable(clickedPosition).map(Vector::clone);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns the handler list, as Bukkit's event system requires.
     *
     * @return the handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
