package dev.shvquu.betternpcs.core.render;

import dev.shvquu.betternpcs.api.npc.NpcType;
import dev.shvquu.betternpcs.api.npc.property.NpcAppearance;
import dev.shvquu.betternpcs.api.npc.property.NpcEquipment;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.api.npc.property.NpcSkin;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;

/**
 * Everything a {@link dev.shvquu.betternpcs.core.version.VersionAdapter} needs to render one NPC.
 *
 * <p>The adapters never see an {@link dev.shvquu.betternpcs.api.npc.Npc}. They receive this frozen
 * value instead, which has three consequences that matter. An adapter cannot accidentally mutate
 * engine state. An adapter cannot read a property the engine did not decide to expose, so the
 * boundary stays narrow enough to keep six implementations in step. And the view can be built once
 * and handed to a broadcast that sends the same packet to fifty players.
 *
 * @param entityId    the entity id the client will know this NPC by
 * @param entityUuid  the entity uuid; for a player NPC this is also the profile uuid
 * @param profileName the name in a player NPC's profile, at most 16 characters
 * @param type        what the client should render
 * @param position    where the NPC stands
 * @param skin        the skin, or {@code null} for the default
 * @param appearance  the state flags
 * @param equipment   what the NPC wears and holds
 * @param displayName the rendered name, used for the tab list entry of a player NPC
 * @since 1.0.0
 */
public record NpcView(
        int entityId,
        UUID entityUuid,
        String profileName,
        NpcType type,
        NpcPosition position,
        NpcSkin skin,
        NpcAppearance appearance,
        NpcEquipment equipment,
        Component displayName) {

    /** The longest name Minecraft accepts in a game profile. */
    public static final int MAX_PROFILE_NAME_LENGTH = 16;

    /**
     * Creates the view.
     *
     * @param entityId    the entity id
     * @param entityUuid  the entity uuid
     * @param profileName the profile name
     * @param type        the entity type to render
     * @param position    the position
     * @param skin        the skin, may be {@code null}
     * @param appearance  the state flags
     * @param equipment   the equipment
     * @param displayName the rendered name
     * @throws NullPointerException     if any argument except {@code skin} is {@code null}
     * @throws IllegalArgumentException if {@code profileName} is blank or too long
     */
    public NpcView {
        Objects.requireNonNull(entityUuid, "entityUuid");
        Objects.requireNonNull(profileName, "profileName");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(displayName, "displayName");

        if (profileName.isBlank() || profileName.length() > MAX_PROFILE_NAME_LENGTH) {
            // A profile name the client refuses disconnects the player with a decoding error rather
            // than simply not showing the NPC, so this is worth refusing early and loudly.
            throw new IllegalArgumentException(
                    "A profile name is 1 to " + MAX_PROFILE_NAME_LENGTH + " characters, was '"
                            + profileName + "'");
        }
    }

    /**
     * Returns the skin to render.
     *
     * @return the skin, or empty for the client's default
     */
    public Optional<NpcSkin> skinIfPresent() {
        return Optional.ofNullable(skin);
    }

    /**
     * Returns whether this NPC is rendered as a fake player.
     *
     * <p>Player NPCs are the only ones needing a profile, a tab list entry before their spawn
     * packet, and a different spawn packet shape on older protocol versions.
     *
     * @return {@code true} for a player NPC
     */
    public boolean isPlayer() {
        return type.isPlayer();
    }
}
