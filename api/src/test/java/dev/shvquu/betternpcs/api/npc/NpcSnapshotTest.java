package dev.shvquu.betternpcs.api.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.shvquu.betternpcs.api.action.ActionDefinition;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.api.npc.property.HologramSettings;
import dev.shvquu.betternpcs.api.npc.property.NpcAppearance;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.api.npc.property.NpcVisibility;
import dev.shvquu.betternpcs.api.skin.SkinSource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NpcSnapshotTest {

    private static final UUID ID = UUID.fromString("4b0d1a26-6f6a-4a6e-9a2e-0a1f2b3c4d5e");
    private static final NpcPosition POSITION = NpcPosition.of("world", 100, 64, 100);

    private static NpcSnapshot.Builder minimal() {
        return NpcSnapshot.builder(ID, "shopkeeper", NpcType.PLAYER, POSITION);
    }

    @Test
    void appliesSensibleDefaults() {
        NpcSnapshot snapshot = minimal().build();

        assertThat(snapshot.displayName()).isEmpty();
        assertThat(snapshot.skinSource()).isEmpty();
        assertThat(snapshot.equipment().isEmpty()).isTrue();
        assertThat(snapshot.appearance()).isEqualTo(NpcAppearance.DEFAULT);
        assertThat(snapshot.visibility()).isEqualTo(NpcVisibility.DEFAULT);
        assertThat(snapshot.hologram()).isEqualTo(HologramSettings.NONE);
        assertThat(snapshot.actions()).isEmpty();
        assertThat(snapshot.metadata()).isEmpty();
        assertThat(snapshot.spawnByDefault()).isTrue();
    }

    @Test
    void keepsActionsInOrder() {
        NpcSnapshot snapshot = minimal()
                .action(InteractionType.RIGHT_CLICK, ActionDefinition.parse("message: hi"))
                .action(InteractionType.RIGHT_CLICK, ActionDefinition.parse("sound: click"))
                .action(InteractionType.RIGHT_CLICK, ActionDefinition.parse("command: spawn"))
                .build();

        // Order is the whole meaning of an action chain: a cooldown check that ran last would let
        // every other action through first.
        assertThat(snapshot.actions(InteractionType.RIGHT_CLICK))
                .extracting(ActionDefinition::type)
                .containsExactly("message", "sound", "command");
    }

    @Test
    void reportsNoActionsForAnUnboundInteraction() {
        NpcSnapshot snapshot = minimal()
                .action(InteractionType.RIGHT_CLICK, ActionDefinition.parse("message: hi"))
                .build();

        assertThat(snapshot.actions(InteractionType.LEFT_CLICK)).isEmpty();
        assertThat(snapshot.actions()).containsOnlyKeys(InteractionType.RIGHT_CLICK);
    }

    @Test
    void dropsInteractionsWhoseActionListIsEmpty() {
        NpcSnapshot snapshot = minimal().actions(InteractionType.LEFT_CLICK, List.of()).build();

        assertThat(snapshot.actions()).isEmpty();
    }

    @Test
    void exposesImmutableCollections() {
        NpcSnapshot snapshot = minimal()
                .action(InteractionType.RIGHT_CLICK, ActionDefinition.parse("message: hi"))
                .metadata("plugin:key", "value")
                .build();

        assertThatThrownBy(() -> snapshot.actions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.actions(InteractionType.RIGHT_CLICK).clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.metadata().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void isNotAffectedByLaterBuilderChanges() {
        NpcSnapshot.Builder builder = minimal();
        NpcSnapshot snapshot = builder.build();

        builder.displayName("<green>Changed").metadata("late", "entry");

        assertThat(snapshot.displayName()).isEmpty();
        assertThat(snapshot.metadata()).isEmpty();
    }

    @Test
    void derivesACopyThatKeepsEverythingElse() {
        NpcSnapshot original = minimal()
                .displayName("<green>Shopkeeper")
                .skinSource(SkinSource.playerName("Notch"))
                .visibility(NpcVisibility.withinDistance(32))
                .action(InteractionType.RIGHT_CLICK, ActionDefinition.parse("command: shop"))
                .metadata("shop:id", "general")
                .spawnByDefault(false)
                .build();

        NpcSnapshot moved = original.toBuilder().position(POSITION.offset(0, 1, 0)).build();

        assertThat(moved.position()).isEqualTo(POSITION.offset(0, 1, 0));
        assertThat(moved.displayName()).contains("<green>Shopkeeper");
        assertThat(moved.skinSource()).contains(SkinSource.playerName("Notch"));
        assertThat(moved.visibility()).isEqualTo(original.visibility());
        assertThat(moved.actions()).isEqualTo(original.actions());
        assertThat(moved.metadata()).isEqualTo(original.metadata());
        assertThat(moved.spawnByDefault()).isFalse();
        assertThat(moved.uniqueId()).isEqualTo(ID);
    }

    @Test
    void roundTripsUnchangedThroughItsBuilder() {
        NpcSnapshot original = minimal()
                .displayName("<green>Shopkeeper")
                .action(InteractionType.SHIFT_RIGHT_CLICK, ActionDefinition.parse("console: say hi"))
                .metadata("a", "1")
                .metadata("b", "2")
                .build();

        assertThat(original.toBuilder().build()).isEqualTo(original);
        assertThat(original.toBuilder().build()).hasSameHashCodeAs(original);
    }

    @Test
    void removesAMetadataEntrySetToNull() {
        NpcSnapshot snapshot = minimal().metadata("key", "value").metadata("key", null).build();

        assertThat(snapshot.metadata()).isEmpty();
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> NpcSnapshot.builder(ID, "  ", NpcType.PLAYER, POSITION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankMetadataKey() {
        assertThatThrownBy(() -> minimal().metadata("  ", "value"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordersMetadataDeterministically() {
        // Storage hashes a snapshot to decide whether a write is needed; a randomised iteration
        // order would make that hash differ between runs and rewrite every NPC on every startup.
        NpcSnapshot snapshot = minimal()
                .metadata("zebra", "1")
                .metadata("alpha", "2")
                .metadata("middle", "3")
                .build();

        assertThat(snapshot.metadata().keySet()).containsExactly("alpha", "middle", "zebra");
    }
}
