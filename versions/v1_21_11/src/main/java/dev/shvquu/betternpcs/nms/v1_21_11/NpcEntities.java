package dev.shvquu.betternpcs.nms.v1_21_11;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.shvquu.betternpcs.api.npc.property.Billboard;
import dev.shvquu.betternpcs.api.npc.property.NpcAppearance;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.api.npc.property.TextAlignment;
import dev.shvquu.betternpcs.api.npc.property.TextDisplayStyle;
import dev.shvquu.betternpcs.core.render.NpcView;
import dev.shvquu.betternpcs.core.render.TextView;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;

/**
 * Builds and caches the detached Minecraft entities the packets are constructed from.
 *
 * <h2>Why entities exist at all for a packet NPC</h2>
 *
 * <p>Several clientbound packets — head rotation, animation, entity event — take an {@code Entity}
 * rather than an id, and entity metadata is a list of typed values whose numeric ids change between
 * Minecraft versions. Building a real entity and never adding it to a level solves both: the
 * constructors are satisfied, and {@code getEntityData()} produces correctly numbered metadata
 * without a single magic number in this file.
 *
 * <p>The entities are never ticked, never added to a {@link ServerLevel} and never seen by any other
 * plugin. They exist purely as packet builders.
 *
 * <h2>Why configuration goes through the Bukkit wrapper</h2>
 *
 * <p>Where a property can be set through {@code entity.getBukkitEntity()} it is. Bukkit's
 * {@code TextDisplay} interface is stable across every version this plugin supports, while the
 * metadata ids behind it were renumbered more than once — setting them by hand is how an adapter
 * silently starts rendering the wrong thing after a Minecraft update.
 */
final class NpcEntities {

    /**
     * Every skin layer switched on: cape, jacket, both sleeves, both trouser legs and the hat.
     *
     * <p>Without this a player NPC renders with its outer skin layers missing, which reads as a
     * broken skin rather than as a setting nobody changed.
     */
    private static final byte ALL_SKIN_LAYERS = 0x7F;

    /** The registry key of the text display entity, looked up rather than referenced by constant. */
    private static final String TEXT_DISPLAY_KEY = "minecraft:text_display";

    private final Map<Integer, Entity> byEntityId = new ConcurrentHashMap<>();

    /**
     * Returns the entity for a view, building it if this is the first time it has been seen.
     *
     * <p>Rebuilt whenever the engine hands out a new entity id, which it does for exactly the
     * changes a client cannot be told about — a new type, a new skin, a new profile.
     *
     * @param view what to build
     * @return the entity, or empty if the NPC's world is not loaded
     */
    Optional<Entity> forNpc(NpcView view) {
        Entity cached = byEntityId.get(view.entityId());
        if (cached != null) {
            return Optional.of(cached);
        }
        return level(view.position()).map(level -> {
            Entity built = build(view, level);
            byEntityId.put(view.entityId(), built);
            return built;
        });
    }

    /**
     * Returns the text display entity for a line, building it if needed.
     *
     * @param view what to build
     * @return the entity, or empty if the world is not loaded
     */
    Optional<Display.TextDisplay> forText(TextView view) {
        Entity cached = byEntityId.get(view.entityId());
        if (cached instanceof Display.TextDisplay display) {
            apply(display, view);
            return Optional.of(display);
        }
        return level(view.position()).map(level -> {
            Display.TextDisplay display = (Display.TextDisplay)
                    typeByKey(TEXT_DISPLAY_KEY).create(level, EntitySpawnReason.COMMAND);
            display.setId(view.entityId());
            display.setUUID(view.entityUuid());
            apply(display, view);
            byEntityId.put(view.entityId(), display);
            return display;
        });
    }

    /**
     * Returns an already-built entity by its id.
     *
     * <p>Used by the calls that have only an id to work with, such as a per-viewer rotation. An
     * empty result means the NPC is not currently rendered, and there is nothing to send.
     *
     * @param entityId the entity id
     * @return the entity, or empty if none has been built for that id
     */
    Optional<Entity> byId(int entityId) {
        return Optional.ofNullable(byEntityId.get(entityId));
    }

    /**
     * Forgets the entities behind the given ids.
     *
     * @param entityIds the ids to release
     */
    void release(int[] entityIds) {
        for (int entityId : entityIds) {
            byEntityId.remove(entityId);
        }
    }

    /**
     * Forgets every entity.
     */
    void clear() {
        byEntityId.clear();
    }

    private Entity build(NpcView view, ServerLevel level) {
        Entity entity = view.isPlayer() ? buildPlayer(view, level) : buildMob(view, level);

        entity.setId(view.entityId());
        entity.setUUID(view.entityUuid());
        entity.setPos(view.position().x(), view.position().y(), view.position().z());
        entity.setYRot(view.position().yaw());
        entity.setXRot(view.position().pitch());
        entity.setYHeadRot(view.position().yaw());

        applyAppearance(entity, view.appearance());
        return entity;
    }

    private ServerPlayer buildPlayer(NpcView view, ServerLevel level) {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();

        GameProfile profile = new GameProfile(view.entityUuid(), view.profileName());
        view.skinIfPresent().ifPresent(skin -> profile.properties().put(
                "textures",
                skin.signature()
                        .map(signature -> new Property("textures", skin.value(), signature))
                        // An unsigned texture will not render on a vanilla client, but a proxy that
                        // forwards profile properties may accept it, so it is sent rather than dropped.
                        .orElseGet(() -> new Property("textures", skin.value()))));

        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());

        // The skin layer byte lives on Player, whose accessor is not public. Reaching it through the
        // Bukkit wrapper is not possible either, so this is the one value written by id — and it is
        // written by asking the entity's own data holder rather than by building a raw DataValue,
        // so the serialiser stays whatever the running version says it is.
        player.getEntityData().set(
                net.minecraft.world.entity.player.Player.DATA_PLAYER_MODE_CUSTOMISATION, ALL_SKIN_LAYERS);
        return player;
    }

    private Entity buildMob(NpcView view, ServerLevel level) {
        Entity entity = typeByKey(view.type().entityType().getKey().toString())
                .create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            throw new IllegalStateException("Could not create an entity of type " + view.type().id());
        }
        return entity;
    }

    /**
     * Looks a Minecraft entity type up from its namespaced key.
     *
     * <p>Done by walking the registry and comparing keys as text, rather than by building a key
     * object or naming a constant on {@code EntityType}. That is not laziness. The key class was
     * renamed between the versions this plugin supports, its lookup methods changed shape more than
     * once, and 26.2 removed the static type constants altogether — while iterating the registry and
     * calling {@code toString()} has worked identically throughout. The walk covers about a hundred
     * and fifty entries and happens once per NPC, behind a cache.
     *
     * @param key the namespaced key, for example {@code minecraft:villager}
     * @return the Minecraft entity type
     * @throws IllegalStateException if this Minecraft version has no such type
     */
    private static EntityType<?> typeByKey(String key) {
        for (EntityType<?> candidate : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
            if (EntityType.getKey(candidate).toString().equals(key)) {
                return candidate;
            }
        }
        throw new IllegalStateException(key + " is not an entity type on this Minecraft version");
    }

    private void applyAppearance(Entity entity, NpcAppearance appearance) {
        // Shared flags, by their vanilla bit positions. setSharedFlag is public precisely so that
        // this can be done without touching the metadata accessor.
        entity.setSharedFlagOnFire(appearance.onFire());
        entity.setShiftKeyDown(appearance.sneaking());
        entity.setInvisible(appearance.invisible());
        entity.setGlowingTag(appearance.glowing());

        entity.setSilent(appearance.silent());
        entity.setNoGravity(!appearance.gravity());
        entity.setPose(appearance.sneaking() ? Pose.CROUCHING : Pose.STANDING);
    }

    private void apply(Display.TextDisplay display, TextView view) {
        display.setPos(view.position().x(), view.position().y(), view.position().z());

        // Configured through the Bukkit interface, which is stable across every supported version,
        // rather than through metadata ids, which are not.
        org.bukkit.entity.TextDisplay bukkit =
                (org.bukkit.entity.TextDisplay) display.getBukkitEntity();

        bukkit.text(view.text());
        bukkit.setBillboard(billboard(view.style().billboard()));
        bukkit.setAlignment(alignment(view.style().alignment()));
        bukkit.setSeeThrough(view.style().seeThrough());
        bukkit.setShadowed(view.style().shadowed());
        bukkit.setTextOpacity(opacity(view.style().textOpacity()));

        view.style().backgroundColor().ifPresent(argb ->
                bukkit.setBackgroundColor(org.bukkit.Color.fromARGB(argb)));

        if (view.style().scale() != 1.0f) {
            org.bukkit.util.Transformation transformation = bukkit.getTransformation();
            transformation.getScale().set(view.style().scale());
            bukkit.setTransformation(transformation);
        }
    }

    private static byte opacity(int configured) {
        // Bukkit encodes "the default" as -1 and only accepts a signed byte for the rest, while the
        // API expresses opacity as 0..255. Fully opaque is the default, so it maps to -1.
        return configured >= TextDisplayStyle.OPAQUE ? (byte) -1 : (byte) configured;
    }

    private static org.bukkit.entity.Display.Billboard billboard(Billboard billboard) {
        return switch (billboard) {
            case FIXED -> org.bukkit.entity.Display.Billboard.FIXED;
            case VERTICAL -> org.bukkit.entity.Display.Billboard.VERTICAL;
            case HORIZONTAL -> org.bukkit.entity.Display.Billboard.HORIZONTAL;
            case CENTER -> org.bukkit.entity.Display.Billboard.CENTER;
        };
    }

    private static org.bukkit.entity.TextDisplay.TextAlignment alignment(TextAlignment alignment) {
        return switch (alignment) {
            case CENTER -> org.bukkit.entity.TextDisplay.TextAlignment.CENTER;
            case LEFT -> org.bukkit.entity.TextDisplay.TextAlignment.LEFT;
            case RIGHT -> org.bukkit.entity.TextDisplay.TextAlignment.RIGHT;
        };
    }

    private static Optional<ServerLevel> level(NpcPosition position) {
        World world = Bukkit.getWorld(position.world());
        return world == null ? Optional.empty() : Optional.of(((CraftWorld) world).getHandle());
    }
}
