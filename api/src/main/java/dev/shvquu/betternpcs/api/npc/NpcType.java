package dev.shvquu.betternpcs.api.npc;

import java.util.Locale;
import java.util.Objects;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

/**
 * What an NPC looks like to the client: a fake player, or one of Minecraft's living entity types.
 *
 * <p>This is a value object over {@link EntityType} rather than an enum of its own. An enum would
 * have to be extended by hand for every mob Mojang adds, and would silently lack whatever the
 * running server supports; delegating means a new mob type works the moment the server knows about
 * it, which is exactly the extensibility the version adapters already provide.
 *
 * <p>Only living entity types are accepted. NPCs carry equipment, animations and nametags, all of
 * which are properties of living entities; a boat or a dropped item would need an entirely
 * different rendering path and is out of scope for this type.
 *
 * <p>Instances are immutable and safe to share across threads.
 *
 * @since 1.0.0
 */
public final class NpcType {

    /**
     * A fake player. The only type that supports skins and tab list entries.
     */
    public static final NpcType PLAYER = new NpcType(EntityType.PLAYER);

    private final EntityType entityType;

    private NpcType(EntityType entityType) {
        this.entityType = entityType;
    }

    /**
     * Returns the NPC type for a Bukkit entity type.
     *
     * @param entityType the entity type to render as
     * @return the NPC type, {@link #PLAYER} for {@link EntityType#PLAYER}
     * @throws NullPointerException     if {@code entityType} is {@code null}
     * @throws IllegalArgumentException if {@code entityType} is not a living entity
     */
    public static NpcType of(EntityType entityType) {
        Objects.requireNonNull(entityType, "entityType");
        if (entityType == EntityType.PLAYER) {
            return PLAYER;
        }
        Class<?> implementation = entityType.getEntityClass();
        if (implementation == null || !LivingEntity.class.isAssignableFrom(implementation)) {
            throw new IllegalArgumentException(
                    "NPCs can only be living entities, but " + entityType.name() + " is not one");
        }
        return new NpcType(entityType);
    }

    /**
     * Parses the serialised form produced by {@link #id()}.
     *
     * <p>Matching is case insensitive so that hand-written configuration files do not have to shout.
     *
     * @param id the type id, for example {@code PLAYER} or {@code villager}
     * @return the NPC type
     * @throws NullPointerException     if {@code id} is {@code null}
     * @throws IllegalArgumentException if no living entity type has that id
     */
    public static NpcType fromId(String id) {
        Objects.requireNonNull(id, "id");
        String normalised = id.trim().toUpperCase(Locale.ROOT);
        // Fall back to the key form ("minecraft:villager" or "villager") that newer API versions use,
        // so that data written by a future release still loads.
        int colon = normalised.indexOf(':');
        if (colon >= 0) {
            normalised = normalised.substring(colon + 1);
        }
        EntityType parsed;
        try {
            parsed = EntityType.valueOf(normalised);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Unknown NPC type: '" + id + "'", unknown);
        }
        return of(parsed);
    }

    /**
     * Returns the Bukkit entity type this NPC is rendered as.
     *
     * @return the entity type, {@link EntityType#PLAYER} for player NPCs
     */
    public EntityType entityType() {
        return entityType;
    }

    /**
     * Returns whether this is a player NPC.
     *
     * <p>Player NPCs are the only ones with skins, tab list entries and a profile, and they are the
     * only ones whose spawn requires a player info update before the spawn packet.
     *
     * @return {@code true} for {@link #PLAYER}
     */
    public boolean isPlayer() {
        return entityType == EntityType.PLAYER;
    }

    /**
     * Returns the stable id used in configuration and storage.
     *
     * @return the uppercase entity type name, for example {@code PLAYER} or {@code VILLAGER}
     */
    public String id() {
        return entityType.name();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof NpcType type && entityType == type.entityType;
    }

    @Override
    public int hashCode() {
        return entityType.hashCode();
    }

    /**
     * Returns {@link #id()}.
     *
     * @return the type id
     */
    @Override
    public String toString() {
        return id();
    }
}
