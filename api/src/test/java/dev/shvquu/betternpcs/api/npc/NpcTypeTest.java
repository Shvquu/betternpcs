package dev.shvquu.betternpcs.api.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NpcTypeTest {

    @Test
    void recognisesThePlayerType() {
        assertThat(NpcType.PLAYER.isPlayer()).isTrue();
        assertThat(NpcType.PLAYER.entityType()).isEqualTo(EntityType.PLAYER);
    }

    @Test
    void returnsTheSharedConstantForPlayer() {
        assertThat(NpcType.of(EntityType.PLAYER)).isSameAs(NpcType.PLAYER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZOMBIE", "SKELETON", "CREEPER", "VILLAGER", "PIG", "COW", "IRON_GOLEM"})
    void acceptsLivingMobs(String name) {
        NpcType type = NpcType.of(EntityType.valueOf(name));

        assertThat(type.isPlayer()).isFalse();
        assertThat(type.id()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARROW", "DROPPED_ITEM", "ITEM", "BOAT", "PAINTING", "EXPERIENCE_ORB"})
    void rejectsNonLivingEntities(String name) {
        // A boat has no equipment, no nametag mechanism and no animations. Accepting one here would
        // push the failure all the way down into a version adapter building a packet it cannot.
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(name);
        } catch (IllegalArgumentException notOnThisVersion) {
            // The Bukkit enum renames things between versions; skipping a name this API does not
            // have is correct, because the point is the rule, not any particular constant.
            return;
        }
        assertThatThrownBy(() -> NpcType.of(entityType)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesAnIdCaseInsensitively() {
        assertThat(NpcType.fromId("villager")).isEqualTo(NpcType.of(EntityType.VILLAGER));
        assertThat(NpcType.fromId("  Player  ")).isEqualTo(NpcType.PLAYER);
    }

    @Test
    void parsesANamespacedKey() {
        // Data written by a future release that serialises the key form still has to load.
        assertThat(NpcType.fromId("minecraft:zombie")).isEqualTo(NpcType.of(EntityType.ZOMBIE));
    }

    @Test
    void rejectsAnUnknownId() {
        assertThatThrownBy(() -> NpcType.fromId("not_a_mob"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not_a_mob");
    }

    @Test
    void roundTripsThroughItsId() {
        NpcType type = NpcType.of(EntityType.SKELETON);

        assertThat(NpcType.fromId(type.id())).isEqualTo(type);
    }

    @Test
    void comparesByEntityType() {
        assertThat(NpcType.of(EntityType.COW)).isEqualTo(NpcType.of(EntityType.COW));
        assertThat(NpcType.of(EntityType.COW)).hasSameHashCodeAs(NpcType.of(EntityType.COW));
        assertThat(NpcType.of(EntityType.COW)).isNotEqualTo(NpcType.of(EntityType.PIG));
    }
}
