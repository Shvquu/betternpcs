package dev.shvquu.betternpcs.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.shvquu.betternpcs.api.action.ActionDefinition;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import dev.shvquu.betternpcs.api.npc.NpcType;
import dev.shvquu.betternpcs.api.npc.property.HologramSettings;
import dev.shvquu.betternpcs.api.npc.property.LookSettings;
import dev.shvquu.betternpcs.api.npc.property.NametagSettings;
import dev.shvquu.betternpcs.api.npc.property.NpcAppearance;
import dev.shvquu.betternpcs.api.npc.property.NpcEquipment;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.api.npc.property.NpcSkin;
import dev.shvquu.betternpcs.api.npc.property.NpcVisibility;
import dev.shvquu.betternpcs.api.skin.SkinSource;
import dev.shvquu.betternpcs.core.config.StorageSettings;
import dev.shvquu.betternpcs.core.config.StorageType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Exercises the repository against a real SQLite database in a temporary folder.
 *
 * <p>No mocked JDBC. Every statement in {@link SqlDialect.Sqlite} is executed by the real driver,
 * which is the only way to find out whether it is valid SQL — a mocked connection would happily
 * accept a statement with a typo in it.
 */
class SqlNpcRepositoryTest {

    @TempDir
    Path dataFolder;

    private SqlNpcRepository repository;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        repository = open();
        repository.initialize().join();
    }

    @AfterEach
    void tearDown() {
        if (repository != null) {
            repository.shutdown().join();
        }
        MockBukkit.unmock();
    }

    private SqlNpcRepository open() {
        Logger logger = Logger.getLogger("SqlNpcRepositoryTest");
        logger.setLevel(Level.OFF);

        StorageSettings settings = new StorageSettings(
                StorageType.SQLITE,
                new StorageSettings.Sqlite("npcs.db"),
                new StorageSettings.Jdbc("localhost", 3306, "db", "user", "", 10, 10_000L, Map.of()),
                new StorageSettings.Jdbc("localhost", 5432, "db", "user", "", 10, 10_000L, Map.of()),
                new StorageSettings.Mongo("mongodb://localhost:27017", "db"));

        return SqlRepositoryFactory.create(settings, dataFolder, logger);
    }

    private static NpcSnapshot.Builder npc(String name) {
        return NpcSnapshot.builder(
                UUID.randomUUID(), name, NpcType.PLAYER, NpcPosition.of("world", 100, 64, -20));
    }

    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("schema")
    class Schema {

        @Test
        void createsTheDatabaseFile() {
            assertThat(dataFolder.resolve("npcs.db")).exists();
        }

        @Test
        void isSafeToApplyTwice() {
            // Every start runs the migrator. Re-running an already-applied migration must be a
            // no-op, not an error about a table that already exists.
            SqlNpcRepository second = open();
            try {
                second.initialize().join();
                assertThat(second.loadAll().join()).isEmpty();
            } finally {
                second.shutdown().join();
            }
        }

        @Test
        void refusesTwoNpcsWithTheSameName() {
            repository.save(npc("shopkeeper").build()).join();

            // Enforced by the database, not only in memory: a hand-edited file must not be able to
            // produce a state the engine would then refuse to load.
            assertThatThrownBy(() -> repository.save(npc("shopkeeper").build()).join())
                    .isInstanceOf(CompletionException.class);
        }

        @Test
        void matchesNamesCaseInsensitively() {
            repository.save(npc("Shopkeeper").build()).join();

            assertThatThrownBy(() -> repository.save(npc("SHOPKEEPER").build()).join())
                    .isInstanceOf(CompletionException.class);
        }
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        void preservesAFullyConfiguredNpc() {
            NpcSnapshot original = npc("shopkeeper")
                    .displayName("<gradient:#00c6ff:#0072ff>Shopkeeper</gradient>")
                    .skinSource(SkinSource.playerName("Notch"))
                    .resolvedSkin(NpcSkin.of("dGV4dHVyZQ==", "c2lnbmF0dXJl"))
                    .equipment(NpcEquipment.builder()
                            .helmet(new ItemStack(Material.GOLDEN_HELMET))
                            .mainHand(new ItemStack(Material.DIAMOND_SWORD, 1))
                            .build())
                    .appearance(NpcAppearance.builder()
                            .glowing(true)
                            .glowColor(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                            .sneaking(true)
                            .listedInTablist(true)
                            .build())
                    .visibility(NpcVisibility.withinDistance(24).withPermission("shop.see"))
                    .look(LookSettings.builder()
                            .mode(dev.shvquu.betternpcs.api.npc.property.LookMode.LOOK_AT_VIEWER)
                            .range(12)
                            .updateInterval(3)
                            .headOnly(false)
                            .build())
                    .nametag(NametagSettings.builder()
                            .text("<gray>[Shop] <npc_name>")
                            .viewDistance(16)
                            .permission("shop.nametag")
                            .build())
                    .hologram(HologramSettings.builder()
                            .line("<yellow>Open")
                            .line("<gray>Right-click")
                            .verticalOffset(0.3)
                            .build())
                    .action(InteractionType.RIGHT_CLICK, ActionDefinition.parse("message: <green>Hi"))
                    .action(InteractionType.RIGHT_CLICK, ActionDefinition.parse("command: shop"))
                    .action(InteractionType.LEFT_CLICK, ActionDefinition.parse("message: ouch"))
                    .metadata("shop:id", "general")
                    .metadata("shop:tier", "2")
                    .spawnByDefault(false)
                    .build();

            repository.save(original).join();

            assertThat(repository.load(original.uniqueId()).join()).contains(original);
        }

        @Test
        void preservesAMinimalNpc() {
            NpcSnapshot original = npc("plain").build();

            repository.save(original).join();

            assertThat(repository.load(original.uniqueId()).join()).contains(original);
        }

        @Test
        void preservesAMobNpc() {
            NpcSnapshot original = NpcSnapshot.builder(
                            UUID.randomUUID(),
                            "guard",
                            NpcType.of(org.bukkit.entity.EntityType.IRON_GOLEM),
                            NpcPosition.of("nether", -1, 70, 5))
                    .build();

            repository.save(original).join();

            assertThat(repository.load(original.uniqueId()).join()).contains(original);
        }

        @Test
        void preservesAwkwardRotations() {
            // Yaw is normalised on construction, so what goes in is not what was written. The stored
            // value must round-trip to the same normalised value.
            NpcSnapshot original = npc("turned")
                    .position(new NpcPosition("world", 0.5, 64.25, -0.5, 179.9f, -89.9f))
                    .build();

            repository.save(original).join();

            assertThat(repository.load(original.uniqueId()).join()).contains(original);
        }
    }

    @Nested
    @DisplayName("operations")
    class Operations {

        @Test
        void updatesAnNpcThatIsAlreadyStored() {
            NpcSnapshot original = npc("shopkeeper").build();
            repository.save(original).join();

            NpcSnapshot moved = original.toBuilder()
                    .position(NpcPosition.of("world", 1, 2, 3))
                    .displayName("<red>Closed")
                    .build();
            repository.save(moved).join();

            assertThat(repository.loadAll().join()).containsExactly(moved);
        }

        @Test
        void writesABatchInOneGo() {
            List<NpcSnapshot> batch = List.of(
                    npc("one").build(), npc("two").build(), npc("three").build());

            repository.saveAll(batch).join();

            assertThat(repository.loadAll().join()).containsExactlyInAnyOrderElementsOf(batch);
        }

        @Test
        void writesNothingWhenABatchIsRejected() {
            repository.save(npc("taken").build()).join();

            // The second entry collides with the existing name, so the whole batch must roll back —
            // a partial write would leave the database describing a state the server was never in.
            List<NpcSnapshot> batch = List.of(npc("fresh").build(), npc("taken").build());

            assertThatThrownBy(() -> repository.saveAll(batch).join())
                    .isInstanceOf(CompletionException.class);

            assertThat(repository.loadAll().join()).hasSize(1);
        }

        @Test
        void deletesAnNpc() {
            NpcSnapshot snapshot = npc("shopkeeper").build();
            repository.save(snapshot).join();

            assertThat(repository.delete(snapshot.uniqueId()).join()).isTrue();
            assertThat(repository.loadAll().join()).isEmpty();
        }

        @Test
        void reportsDeletingSomethingThatIsNotThere() {
            assertThat(repository.delete(UUID.randomUUID()).join()).isFalse();
        }

        @Test
        void reportsAnUnknownNpcAsAbsent() {
            assertThat(repository.load(UUID.randomUUID()).join()).isEmpty();
        }

        @Test
        void acceptsAnEmptyBatch() {
            assertThat(repository.saveAll(List.of()).join()).isNull();
        }
    }

    @Nested
    @DisplayName("persistence across restarts")
    class Restarts {

        @Test
        void keepsNpcsWhenTheRepositoryIsReopened() {
            NpcSnapshot snapshot = npc("shopkeeper")
                    .action(InteractionType.RIGHT_CLICK, ActionDefinition.parse("command: shop"))
                    .build();
            repository.save(snapshot).join();
            repository.shutdown().join();

            repository = open();
            repository.initialize().join();

            assertThat(repository.loadAll().join()).containsExactly(snapshot);
        }
    }

    @Nested
    @DisplayName("shutdown")
    class Shutdown {

        @Test
        void refusesWorkAfterwards() {
            repository.shutdown().join();

            assertThatThrownBy(() -> repository.loadAll().join())
                    .isInstanceOf(CompletionException.class)
                    .hasRootCauseInstanceOf(IllegalStateException.class);
        }

        @Test
        void isSafeToCallTwice() {
            repository.shutdown().join();

            assertThat(repository.shutdown().join()).isNull();
        }
    }

    @Test
    void survivesACorruptDataColumn() throws Exception {
        NpcSnapshot good = npc("good").build();
        NpcSnapshot broken = npc("broken").build();
        repository.saveAll(List.of(good, broken)).join();

        corruptDataColumn(broken.uniqueId());

        // One unreadable row must not cost the server every other NPC.
        assertThat(repository.loadAll().join()).containsExactly(good);
    }

    private void corruptDataColumn(UUID uniqueId) throws Exception {
        Path file = dataFolder.resolve("npcs.db");
        assertThat(Files.exists(file)).isTrue();

        try (var connection = repository.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "UPDATE betternpcs_npcs SET data = ? WHERE uuid = ?")) {
            statement.setString(1, "{not valid json");
            statement.setString(2, uniqueId.toString());
            statement.executeUpdate();
        }
    }

    @Test
    void describesItselfWithoutCredentials() {
        assertThat(repository.describe()).contains("SQLITE").contains("npcs.db");
    }
}
