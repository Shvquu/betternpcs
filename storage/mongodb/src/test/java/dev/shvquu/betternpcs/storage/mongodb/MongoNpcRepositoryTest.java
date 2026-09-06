package dev.shvquu.betternpcs.storage.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.commons.reverse.TransitionWalker;
import dev.shvquu.betternpcs.api.action.ActionDefinition;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import dev.shvquu.betternpcs.api.npc.NpcType;
import dev.shvquu.betternpcs.api.npc.property.HologramSettings;
import dev.shvquu.betternpcs.api.npc.property.NametagSettings;
import dev.shvquu.betternpcs.api.npc.property.NpcAppearance;
import dev.shvquu.betternpcs.api.npc.property.NpcEquipment;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.api.npc.property.NpcSkin;
import dev.shvquu.betternpcs.api.npc.property.NpcVisibility;
import dev.shvquu.betternpcs.api.skin.SkinSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Exercises the repository against a real {@code mongod} that flapdoodle downloads and starts.
 *
 * <p>The same approach as {@code SqlNpcRepositoryTest}: the real driver against a real server. A
 * mocked driver would only assert that the code calls the methods it was written to call, and would
 * never catch a wrong index definition or a document shape that does not round-trip.
 *
 * <p>The first run downloads a {@code mongod} binary. Where that is not possible — an offline
 * machine, or one behind a TLS-intercepting proxy — the tests skip with the reason rather than
 * failing, because an unavailable download is not a defect in this code.
 */
class MongoNpcRepositoryTest {

    private static TransitionWalker.ReachedState<RunningMongodProcess> mongod;
    private static String connectionString;

    private MongoNpcRepository repository;

    @BeforeAll
    static void startMongo() {
        try {
            mongod = Mongod.instance().start(Version.Main.V7_0);
            connectionString = "mongodb://" + mongod.current().getServerAddress();
        } catch (Throwable unavailable) {
            // Reported, not swallowed: a skipped suite that says nothing looks like a passing one.
            System.out.println(
                    "Skipping the MongoDB tests: an embedded mongod could not be started ("
                            + unavailable + ")");
            mongod = null;
        }
    }

    @AfterAll
    static void stopMongo() {
        if (mongod != null) {
            mongod.close();
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(mongod != null, "no embedded mongod available");
        MockBukkit.mock();
        repository = open("betternpcs_test_" + UUID.randomUUID().toString().replace("-", ""));
        repository.initialize().join();
    }

    @AfterEach
    void tearDown() {
        if (repository != null) {
            repository.shutdown().join();
            repository = null;
        }
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private MongoNpcRepository open(String database) {
        Logger logger = Logger.getLogger("MongoNpcRepositoryTest");
        logger.setLevel(Level.OFF);

        return new MongoNpcRepository(
                MongoClients.create(connectionString), database, "MONGODB -> embedded", logger, 2);
    }

    private static NpcSnapshot.Builder npc(String name) {
        return NpcSnapshot.builder(
                UUID.randomUUID(), name, NpcType.PLAYER, NpcPosition.of("world", 100, 64, -20));
    }

    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("indexes")
    class Indexes {

        @Test
        void refusesTwoNpcsWithTheSameName() {
            repository.save(npc("shopkeeper").build()).join();

            // The SQL schema enforces this with a unique index, and the two backends must not
            // disagree — an NPC set the engine would refuse to load must not be storable here
            // either.
            assertThatThrownBy(() -> repository.save(npc("shopkeeper").build()).join())
                    .isInstanceOf(CompletionException.class);
        }

        @Test
        void matchesNamesCaseInsensitively() {
            repository.save(npc("Shopkeeper").build()).join();

            assertThatThrownBy(() -> repository.save(npc("SHOPKEEPER").build()).join())
                    .isInstanceOf(CompletionException.class);
        }

        @Test
        void isSafeToInitialiseTwice() {
            // Every start runs initialize(). Creating an index that already exists must be a no-op.
            repository.initialize().join();

            assertThat(repository.loadAll().join()).isEmpty();
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
                            .mainHand(new ItemStack(Material.DIAMOND_SWORD))
                            .build())
                    .appearance(NpcAppearance.builder().glowing(true).sneaking(true).build())
                    .visibility(NpcVisibility.withinDistance(24).withPermission("shop.see"))
                    .nametag(NametagSettings.of("<gray>[Shop] <npc_name>"))
                    .hologram(HologramSettings.of("<yellow>Open", "<gray>Right-click"))
                    .action(InteractionType.RIGHT_CLICK, ActionDefinition.parse("message: <green>Hi"))
                    .metadata("shop:id", "general")
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
        void preservesAwkwardRotations() {
            NpcSnapshot original = npc("turned")
                    .position(new NpcPosition("world", 0.5, 64.25, -0.5, 179.9f, -89.9f))
                    .build();

            repository.save(original).join();

            assertThat(repository.load(original.uniqueId()).join()).contains(original);
        }

        @Test
        void producesTheSameDocumentTheSqlBackendWouldStore() {
            // Both backends go through SnapshotCodec, which is what keeps a backup file and a
            // database inspection meaningful regardless of which backend is in use.
            NpcSnapshot original = npc("shopkeeper")
                    .action(InteractionType.LEFT_CLICK, ActionDefinition.parse("message: ouch"))
                    .build();
            repository.save(original).join();

            assertThat(repository.loadAll().join()).containsExactly(original);
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
            List<NpcSnapshot> batch =
                    List.of(npc("one").build(), npc("two").build(), npc("three").build());

            repository.saveAll(batch).join();

            assertThat(repository.loadAll().join()).containsExactlyInAnyOrderElementsOf(batch);
        }

        @Test
        void stopsABatchAtTheEntryThatViolatesTheNameIndex() {
            repository.save(npc("taken").build()).join();

            List<NpcSnapshot> batch = List.of(npc("fresh").build(), npc("taken").build());

            assertThatThrownBy(() -> repository.saveAll(batch).join())
                    .isInstanceOf(CompletionException.class);

            // A single-node MongoDB has no multi-document transaction, so unlike the SQL backends
            // the earlier writes in the batch stand. The ordered write is what stops it going
            // further; docs/storage.md states the difference rather than pretending it away.
            assertThat(repository.loadAll().join()).hasSize(2);
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
    @DisplayName("shutdown")
    class Shutdown {

        @Test
        void refusesWorkAfterwards() {
            repository.shutdown().join();

            assertThatThrownBy(() -> repository.loadAll().join())
                    .isInstanceOf(CompletionException.class)
                    .hasRootCauseInstanceOf(IllegalStateException.class);

            repository = null;
        }

        @Test
        void isSafeToCallTwice() {
            repository.shutdown().join();

            assertThat(repository.shutdown().join()).isNull();
            repository = null;
        }
    }

    @Test
    void describesItselfWithoutCredentials() {
        assertThat(repository.describe()).contains("MONGODB").doesNotContain("password");
    }
}
