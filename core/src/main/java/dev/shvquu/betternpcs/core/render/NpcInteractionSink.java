package dev.shvquu.betternpcs.core.render;

import dev.shvquu.betternpcs.api.interaction.InteractionType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

/**
 * Where a version adapter reports that a player interacted with a fake entity.
 *
 * <p>Packet NPCs do not exist on the server, so no Bukkit event is ever fired for them. The adapter
 * reads the raw interact packet and calls this instead.
 *
 * <h2>Threading</h2>
 *
 * <p>Adapters call this from the network thread, which is the only thread the packet is available
 * on. Implementations are responsible for moving the work to the main thread before touching
 * anything else; adapters must not do that hop themselves, because deciding what is worth
 * scheduling — an interaction with an unknown entity id is not — is the engine's job and doing it
 * first avoids scheduling a task per click for every arrow a player shoots.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface NpcInteractionSink {

    /**
     * Reports an interaction with a fake entity.
     *
     * <p>Called for every entity a player interacts with, including real ones the engine knows
     * nothing about. Implementations must therefore treat an unknown {@code entityId} as normal and
     * return quickly.
     *
     * @param player      the player who interacted
     * @param entityId    the entity id from the packet
     * @param interaction what the player did
     * @param hand        the hand used, or {@code null} for an attack, which the protocol does not
     *                    attribute to a hand
     * @param clickedAt   where on the hit box the click landed relative to the entity's feet, or
     *                    {@code null} for the forms of the packet that do not carry it
     */
    void onInteract(
            Player player,
            int entityId,
            InteractionType interaction,
            EquipmentSlot hand,
            Vector clickedAt);
}
