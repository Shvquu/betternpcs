package dev.shvquu.betternpcs.nms.v1_21_11;

import com.mojang.datafixers.util.Pair;
import dev.shvquu.betternpcs.api.animation.NpcAnimation;
import dev.shvquu.betternpcs.core.render.AdapterContext;
import dev.shvquu.betternpcs.core.render.NpcView;
import dev.shvquu.betternpcs.core.render.TextView;
import dev.shvquu.betternpcs.core.version.VersionAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Version adapter for Minecraft 1.21.9 - 1.21.11.
 *
 * <p>Compiled against the Mojang-mapped 1.21.9 - 1.21.11 dev bundle. Paper has shipped a Mojang-mapped runtime
 * since 1.20.5, so no reobfuscation is involved on any supported version.
 *
 * <p>All the interesting decisions are in {@link NpcEntities}, which explains why packet NPCs are
 * built from real — but detached — Minecraft entities. This class is the translation layer: it turns
 * the engine's frozen views into packets and writes them to connections.
 */
public final class V1_21_11Adapter implements VersionAdapter {

    /**
     * Where fake entity ids start, counting down.
     *
     * <p>The server allocates real entity ids upwards from one. Counting down from the top of the
     * range means a collision needs a session that has spawned two billion entities, and a collision
     * matters: a client told about two entities with the same id renders neither correctly, and the
     * result looks like a rendering bug rather than an id clash.
     */
    private static final int FIRST_FAKE_ENTITY_ID = Integer.MAX_VALUE;

    private final AtomicInteger nextEntityId = new AtomicInteger(FIRST_FAKE_ENTITY_ID);
    private final NpcEntities entities = new NpcEntities();
    private final InteractionListener interactions = new InteractionListener();

    private AdapterContext context;

    /** Creates the adapter. Invoked reflectively by the resolver. */
    public V1_21_11Adapter() {
        // No NMS access during construction: the adapter is instantiated while the plugin enables,
        // before the server is necessarily ready to hand out entity or level state.
    }

    @Override
    public String describe() {
        return "Paper 1.21.9 - 1.21.11 (Mojang-mapped)";
    }

    @Override
    public void enable(AdapterContext adapterContext) {
        this.context = Objects.requireNonNull(adapterContext, "context");
        interactions.enable(adapterContext);
    }

    @Override
    public void disable() {
        interactions.disable();
        entities.clear();
        this.context = null;
    }

    @Override
    public int allocateEntityId() {
        return nextEntityId.getAndDecrement();
    }

    @Override
    public boolean supportsTextDisplays() {
        return true;
    }

    @Override
    public void trackPlayer(Player player) {
        interactions.attach(player);
    }

    @Override
    public void untrackPlayer(Player player) {
        interactions.detach(player);
    }

    // ---------------------------------------------------------------------------------------------
    // NPC rendering
    // ---------------------------------------------------------------------------------------------

    @Override
    public void spawnNpc(NpcView view, Collection<? extends Player> viewers) {
        entities.forNpc(view).ifPresent(entity -> {
            List<Packet<?>> packets = new ArrayList<>(4);

            if (entity instanceof ServerPlayer fakePlayer) {
                // The profile has to reach the client before the spawn packet, or it renders nothing
                // at all — the entity is a player and the client resolves its skin from the list.
                packets.add(new ClientboundPlayerInfoUpdatePacket(
                        EnumSet.of(
                                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                        List.of(fakePlayer)));
            }

            packets.add(new ClientboundAddEntityPacket(
                    view.entityId(),
                    view.entityUuid(),
                    view.position().x(),
                    view.position().y(),
                    view.position().z(),
                    view.position().pitch(),
                    view.position().yaw(),
                    entity.getType(),
                    0,
                    Vec3.ZERO,
                    view.position().yaw()));

            packets.add(metadataPacket(entity, view.entityId()));
            packets.add(new ClientboundRotateHeadPacket(entity, angleToByte(view.position().yaw())));

            send(viewers, packets);
        });
    }

    @Override
    public void hideFromTabList(NpcView view, Collection<? extends Player> viewers) {
        if (!view.isPlayer()) {
            return;
        }
        send(viewers, List.of(new ClientboundPlayerInfoRemovePacket(List.of(view.entityUuid()))));
    }

    @Override
    public void removeEntities(int[] entityIds, Collection<? extends Player> viewers) {
        if (entityIds.length == 0) {
            return;
        }
        send(viewers, List.of(new ClientboundRemoveEntitiesPacket(entityIds)));
        entities.release(entityIds);
    }

    @Override
    public void updateMetadata(NpcView view, Collection<? extends Player> viewers) {
        entities.forNpc(view).ifPresent(entity ->
                send(viewers, List.of(metadataPacket(entity, view.entityId()))));
    }

    @Override
    public void updateEquipment(NpcView view, Collection<? extends Player> viewers) {
        if (view.equipment().isEmpty()) {
            return;
        }

        List<Pair<net.minecraft.world.entity.EquipmentSlot, net.minecraft.world.item.ItemStack>> slots =
                new ArrayList<>();
        for (Map.Entry<org.bukkit.inventory.EquipmentSlot, ItemStack> worn
                : view.equipment().asMap().entrySet()) {
            net.minecraft.world.entity.EquipmentSlot slot = toNms(worn.getKey());
            if (slot != null) {
                slots.add(new Pair<>(slot, CraftItemStack.asNMSCopy(worn.getValue())));
            }
        }
        if (!slots.isEmpty()) {
            send(viewers, List.of(new ClientboundSetEquipmentPacket(view.entityId(), slots)));
        }
    }

    @Override
    public void teleport(NpcView view, Collection<? extends Player> viewers) {
        entities.forNpc(view).ifPresent(entity -> {
            entity.setPos(view.position().x(), view.position().y(), view.position().z());

            send(viewers, List.of(
                    ClientboundTeleportEntityPacket.teleport(
                            view.entityId(),
                            new PositionMoveRotation(
                                    new Vec3(view.position().x(), view.position().y(), view.position().z()),
                                    Vec3.ZERO,
                                    view.position().yaw(),
                                    view.position().pitch()),
                            java.util.Set.<Relative>of(),
                            false),
                    // The teleport packet carries the body rotation; the head is separate, and a
                    // client left with the old head rotation looks like the NPC is glancing away.
                    new ClientboundRotateHeadPacket(entity, angleToByte(view.position().yaw()))));
        });
    }

    @Override
    public void rotate(
            int entityId, float yaw, float pitch, boolean headOnly, Collection<? extends Player> viewers) {

        Entity entity = entities.byId(entityId).orElse(null);
        if (entity == null) {
            return;
        }

        List<Packet<?>> packets = new ArrayList<>(2);
        packets.add(new ClientboundRotateHeadPacket(entity, angleToByte(yaw)));

        if (!headOnly) {
            // The body turns through the move packet. Sent with no positional change, which is what
            // the vanilla server itself does for an entity that turns on the spot.
            packets.add(new net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.Rot(
                    entityId, angleToByte(yaw), angleToByte(pitch), false));
        }
        send(viewers, packets);
    }

    @Override
    public void playAnimation(
            NpcView view, NpcAnimation animation, Collection<? extends Player> viewers) {

        entities.forNpc(view).ifPresent(entity -> {
            switch (animation.kind()) {
                case ANIMATION -> send(viewers, List.of(
                        new ClientboundAnimatePacket(entity, animationId(animation))));
                case ENTITY_EVENT -> send(viewers, List.of(
                        new ClientboundEntityEventPacket(entity, entityEventId(animation))));
                case PARTICLE -> spawnParticles(view, animation, viewers);
            }
        });
    }

    private void spawnParticles(
            NpcView view, NpcAnimation animation, Collection<? extends Player> viewers) {

        org.bukkit.Particle particle = switch (animation) {
            case HEART -> org.bukkit.Particle.HEART;
            case FLAME -> org.bukkit.Particle.FLAME;
            case SMOKE -> org.bukkit.Particle.SMOKE;
            default -> null;
        };
        if (particle == null) {
            return;
        }
        view.position().toLocation().ifPresent(location ->
                viewers.forEach(viewer ->
                        viewer.spawnParticle(particle, location.clone().add(0, 1, 0), 10, 0.4, 0.6, 0.4)));
    }

    /**
     * Returns the vanilla animation id for an animation packet.
     *
     * @param animation the animation
     * @return the id from {@link ClientboundAnimatePacket}
     */
    private static int animationId(NpcAnimation animation) {
        return switch (animation) {
            case SWING_MAIN_HAND -> ClientboundAnimatePacket.SWING_MAIN_HAND;
            case SWING_OFF_HAND -> ClientboundAnimatePacket.SWING_OFF_HAND;
            default -> ClientboundAnimatePacket.SWING_MAIN_HAND;
        };
    }

    /**
     * Returns the vanilla entity event id for an effect delivered as an entity event.
     *
     * <p>These are the numbers from {@code EntityEvent}, which is not a public enum. They have been
     * stable for many versions, and being wrong shows up as the wrong particle rather than as a
     * malformed packet, which is why they are acceptable here.
     *
     * @param animation the animation
     * @return the event id
     */
    private static byte entityEventId(NpcAnimation animation) {
        return switch (animation) {
            case TAKE_DAMAGE -> (byte) 2;
            case CRITICAL_HIT -> (byte) 4;
            case MAGIC_CRITICAL_HIT -> (byte) 5;
            case VILLAGER_ANGRY -> (byte) 13;
            case VILLAGER_HAPPY -> (byte) 14;
            case TOTEM -> (byte) 35;
            default -> (byte) 2;
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Floating text
    // ---------------------------------------------------------------------------------------------

    @Override
    public void spawnText(TextView view, Player viewer) {
        entities.forText(view).ifPresent(display -> send(List.of(viewer), List.of(
                new ClientboundAddEntityPacket(
                        view.entityId(),
                        view.entityUuid(),
                        view.position().x(),
                        view.position().y(),
                        view.position().z(),
                        0.0f,
                        0.0f,
                        display.getType(),
                        0,
                        Vec3.ZERO,
                        0.0),
                metadataPacket(display, view.entityId()))));
    }

    @Override
    public void updateText(TextView view, Player viewer) {
        entities.forText(view).ifPresent(display -> send(List.of(viewer), List.of(
                metadataPacket(display, view.entityId()),
                ClientboundTeleportEntityPacket.teleport(
                        view.entityId(),
                        new PositionMoveRotation(
                                new Vec3(view.position().x(), view.position().y(), view.position().z()),
                                Vec3.ZERO,
                                0.0f,
                                0.0f),
                        java.util.Set.<Relative>of(),
                        false))));
    }

    // ---------------------------------------------------------------------------------------------
    // Plumbing
    // ---------------------------------------------------------------------------------------------

    private static ClientboundSetEntityDataPacket metadataPacket(Entity entity, int entityId) {
        // packAll rather than packDirty: a client that has just been sent the spawn packet has no
        // previous state, and packDirty would send nothing at all for an entity nobody has ticked.
        return new ClientboundSetEntityDataPacket(entityId, entity.getEntityData().packAll());
    }

    private void send(Collection<? extends Player> viewers, List<Packet<?>> packets) {
        for (Player viewer : viewers) {
            ServerPlayer handle = ((CraftPlayer) viewer).getHandle();
            for (Packet<?> packet : packets) {
                handle.connection.send(packet);
            }
        }
    }

    /**
     * Converts an angle in degrees to the single signed byte the protocol uses.
     *
     * @param degrees the angle
     * @return the encoded angle
     */
    private static byte angleToByte(float degrees) {
        return (byte) Math.round(degrees * 256.0f / 360.0f);
    }

    private static net.minecraft.world.entity.EquipmentSlot toNms(
            org.bukkit.inventory.EquipmentSlot slot) {
        return switch (slot) {
            case HAND -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            case OFF_HAND -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            case FEET -> net.minecraft.world.entity.EquipmentSlot.FEET;
            case LEGS -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case CHEST -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case HEAD -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case BODY -> net.minecraft.world.entity.EquipmentSlot.BODY;
            // Slots added by later versions reach this branch. Skipping one is a missing item on an
            // NPC; guessing would be a malformed packet.
            default -> null;
        };
    }

    /**
     * Returns the context the engine supplied.
     *
     * @return the context, or empty before {@link #enable(AdapterContext)}
     */
    Optional<AdapterContext> context() {
        return Optional.ofNullable(context);
    }
}
