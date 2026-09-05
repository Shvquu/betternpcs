package dev.shvquu.betternpcs.nms.v1_21_8;

import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.core.render.AdapterContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

/**
 * Reads interaction packets off players' connections.
 *
 * <p>A packet NPC does not exist on the server, so no Bukkit event is ever fired when a player
 * clicks one. The only place the click is visible is the raw packet, which is why this exists.
 *
 * <h2>Threading</h2>
 *
 * <p>Everything here runs on a netty thread. The handler deliberately does no filtering and no work
 * beyond decoding: whether the entity id belongs to an NPC, and what to do about it, is the engine's
 * decision, and it is the engine that moves onto the main thread.
 *
 * <p>The packet is always passed on. Swallowing it would break every other plugin's handling of
 * interactions with real entities.
 */
final class InteractionListener {

    /** The name the handler is registered under, so it can be removed again. */
    private static final String HANDLER_NAME = "betternpcs-interactions";

    private final Set<UUID> attached = ConcurrentHashMap.newKeySet();
    private volatile AdapterContext context;

    /**
     * Records the context handlers report to.
     *
     * @param adapterContext the engine handles
     */
    void enable(AdapterContext adapterContext) {
        this.context = Objects.requireNonNull(adapterContext, "context");
    }

    /**
     * Stops reporting interactions.
     *
     * <p>Handlers already in a pipeline are left in place and become inert. Removing them would mean
     * touching every connection from the main thread during shutdown, and an inert handler that
     * forwards every packet costs nothing.
     */
    void disable() {
        this.context = null;
        attached.clear();
    }

    /**
     * Adds the handler to a player's pipeline.
     *
     * @param player the player to watch
     */
    void attach(Player player) {
        AdapterContext current = context;
        if (current == null || !attached.add(player.getUniqueId())) {
            return;
        }
        try {
            Channel channel = channelOf(player);
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                return;
            }
            // Before "packet_handler", the vanilla handler that consumes the packet: after it, the
            // packet has already been dispatched and there is nothing left to read.
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new Handler(player, current));
        } catch (RuntimeException failure) {
            attached.remove(player.getUniqueId());
            current.logger().log(Level.WARNING, failure, () ->
                    "Could not watch " + player.getName() + "'s connection for NPC interactions. "
                            + "That player will not be able to click NPCs.");
        }
    }

    /**
     * Removes the handler from a player's pipeline.
     *
     * @param player the player to stop watching
     */
    void detach(Player player) {
        if (!attached.remove(player.getUniqueId())) {
            return;
        }
        try {
            Channel channel = channelOf(player);
            // Queued onto the channel's own thread: netty forbids touching a pipeline from anywhere
            // else, and a quit event arrives on the main thread.
            channel.eventLoop().execute(() -> {
                if (channel.pipeline().get(HANDLER_NAME) != null) {
                    channel.pipeline().remove(HANDLER_NAME);
                }
            });
        } catch (RuntimeException alreadyGone) {
            // The connection has already been torn down, which is the common case on quit.
        }
    }

    private static Channel channelOf(Player player) {
        return ((CraftPlayer) player).getHandle().connection.connection.channel;
    }

    /**
     * The pipeline handler itself.
     */
    private static final class Handler extends ChannelDuplexHandler {

        private final Player player;
        private final AdapterContext context;

        private Handler(Player player, AdapterContext context) {
            this.player = player;
            this.context = context;
        }

        @Override
        public void channelRead(ChannelHandlerContext channelContext, Object message) throws Exception {
            if (message instanceof ServerboundInteractPacket interact) {
                try {
                    decode(interact);
                } catch (RuntimeException failure) {
                    // A failure here must never drop the packet: doing so would break interactions
                    // with real entities for everyone else on the server.
                    context.logger().log(Level.WARNING, failure,
                            () -> "Could not read an interaction from " + player.getName() + ".");
                }
            }
            super.channelRead(channelContext, message);
        }

        private void decode(ServerboundInteractPacket packet) {
            int entityId = packet.getEntityId();
            // Taken from the packet rather than from the player's own state: the two can disagree,
            // because the client decides it is sneaking a moment before the server is told.
            boolean sneaking = packet.isUsingSecondaryAction();

            packet.dispatch(new ServerboundInteractPacket.Handler() {

                @Override
                public void onInteraction(InteractionHand hand) {
                    report(entityId, true, sneaking, hand, null);
                }

                @Override
                public void onInteraction(InteractionHand hand, Vec3 position) {
                    report(entityId, true, sneaking, hand,
                            new Vector(position.x, position.y, position.z));
                }

                @Override
                public void onAttack() {
                    report(entityId, false, sneaking, null, null);
                }
            });
        }

        private void report(
                int entityId,
                boolean rightClick,
                boolean sneaking,
                InteractionHand hand,
                Vector clickedAt) {

            EquipmentSlot slot = hand == null
                    ? null
                    : hand == InteractionHand.MAIN_HAND ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;

            context.interactions().onInteract(
                    player, entityId, InteractionType.of(rightClick, sneaking), slot, clickedAt);
        }
    }
}
