package dev.shvquu.betternpcs.api.event;

import dev.shvquu.betternpcs.api.npc.Npc;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player attacks an NPC that is not invulnerable.
 *
 * <p>Follows {@link NpcInteractEvent}, and only for NPCs whose
 * {@link dev.shvquu.betternpcs.api.npc.property.NpcAppearance#invulnerable()} is {@code false}. It
 * exists for the plugins that care about combat specifically and would otherwise have to filter
 * every interaction; a listener that just wants to know about clicks should use
 * {@link NpcInteractEvent}.
 *
 * <p>BetterNPCs does not itself model NPC health. {@link #getDamage()} is the damage the player's
 * attack would have dealt, offered so that a combat or quest plugin can apply its own rules;
 * nothing happens to the NPC if no plugin acts on it.
 *
 * <p>Cancelling suppresses the hurt animation the engine would otherwise play.
 *
 * @since 1.0.0
 */
public class NpcDamageEvent extends NpcEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player attacker;
    private double damage;
    private boolean cancelled;

    /**
     * Creates the event.
     *
     * @param npc      the NPC that was attacked
     * @param attacker the player who attacked it
     * @param damage   the damage the attack would deal, not negative
     * @throws NullPointerException     if {@code npc} or {@code attacker} is {@code null}
     * @throws IllegalArgumentException if {@code damage} is negative or not finite
     */
    public NpcDamageEvent(Npc npc, Player attacker, double damage) {
        super(npc);
        this.attacker = Objects.requireNonNull(attacker, "attacker");
        this.damage = validateDamage(damage);
    }

    private static double validateDamage(double damage) {
        if (!Double.isFinite(damage) || damage < 0.0) {
            throw new IllegalArgumentException("Damage must be finite and not negative, was " + damage);
        }
        return damage;
    }

    /**
     * Returns the player who attacked.
     *
     * @return the attacker
     */
    public Player getAttacker() {
        return attacker;
    }

    /**
     * Returns the damage the attack would deal.
     *
     * @return the damage, never negative
     */
    public double getDamage() {
        return damage;
    }

    /**
     * Changes the damage other listeners see.
     *
     * @param damage the new damage
     * @throws IllegalArgumentException if {@code damage} is negative or not finite
     */
    public void setDamage(double damage) {
        this.damage = validateDamage(damage);
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
