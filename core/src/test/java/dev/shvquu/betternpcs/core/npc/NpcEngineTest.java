package dev.shvquu.betternpcs.core.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.shvquu.betternpcs.api.action.ActionDefinition;
import dev.shvquu.betternpcs.api.event.NpcCreateEvent;
import dev.shvquu.betternpcs.api.event.NpcDeleteEvent;
import dev.shvquu.betternpcs.api.event.NpcDespawnEvent;
import dev.shvquu.betternpcs.api.event.NpcSpawnEvent;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.api.npc.Npc;
import dev.shvquu.betternpcs.api.npc.NpcState;
import dev.shvquu.betternpcs.api.npc.NpcType;
import dev.shvquu.betternpcs.api.npc.property.HologramSettings;
import dev.shvquu.betternpcs.api.npc.property.LookMode;
import dev.shvquu.betternpcs.api.npc.property.LookSettings;
import dev.shvquu.betternpcs.api.npc.property.NametagSettings;
import dev.shvquu.betternpcs.api.npc.property.NpcAppearance;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.api.npc.property.NpcVisibility;
import dev.shvquu.betternpcs.core.engine.EngineHarness;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class NpcEngineTest {

    @TempDir
    Path languagesFolder;

    private ServerMock server;
    private EngineHarness engine;
    private World world;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        engine = new EngineHarness(languagesFolder);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private NpcPosition origin() {
        return NpcPosition.of("world", 0, 64, 0);
    }

    private NpcHandle create(String name) {
        return (NpcHandle) engine.manager.create(name, NpcType.PLAYER, origin());
    }

    private static String plainNextMessage(PlayerMock player) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(player.nextComponentMessage());
    }

    private PlayerMock playerAt(String name, double x, double y, double z) {
        PlayerMock player = server.addPlayer(name);
        player.setLocation(new Location(world, x, y, z));
        engine.onlinePlayers(List.copyOf(server.getOnlinePlayers()));
        return player;
    }

    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("creation and deletion")
    class CreationAndDeletion {

        @Test
        void registersANewNpcUnderItsName() {
            NpcHandle npc = create("shopkeeper");

            assertThat(npc.state()).isEqualTo(NpcState.CREATED);
            assertThat(npc.isDirty()).as("a new NPC must be written on the next save").isTrue();
            assertThat(engine.manager.byName("SHOPKEEPER")).contains(npc);
            assertThat(engine.manager.count()).isEqualTo(1);
        }

        @Test
        void refusesADuplicateName() {
            create("shopkeeper");

            assertThatThrownBy(() -> create("ShopKeeper"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        void refusesANameThatCannotBeTypedIntoACommand() {
            assertThatThrownBy(() -> engine.manager.create("two words", NpcType.PLAYER, origin()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void firesTheCreateEventBeforeRegistering() {
            engine.events.listen(event -> {
                if (event instanceof NpcCreateEvent create) {
                    // The NPC exists and can be configured, but is not yet findable — a half-created
                    // NPC other plugins could act on would be worse than not seeing it at all.
                    assertThat(engine.registry.contains(create.getNpc().name())).isFalse();
                }
            });

            create("shopkeeper");

            assertThat(engine.events.count(NpcCreateEvent.class)).isEqualTo(1);
        }

        @Test
        void refusesToCreateWhenAListenerCancels() {
            engine.events.listen(event -> {
                if (event instanceof NpcCreateEvent create) {
                    create.setCancelled(true);
                }
            });

            assertThatThrownBy(() -> create("shopkeeper")).isInstanceOf(IllegalStateException.class);
            assertThat(engine.manager.count()).isZero();
        }

        @Test
        void deletesFromMemoryAndStorage() {
            NpcHandle npc = create("shopkeeper");
            npc.save().join();

            assertThat(engine.manager.delete(npc).join()).isTrue();

            assertThat(engine.manager.count()).isZero();
            assertThat(engine.repository.size()).isZero();
            assertThat(npc.isRemoved()).isTrue();
        }

        @Test
        void keepsTheNpcWhenDeletionIsCancelled() {
            NpcHandle npc = create("shopkeeper");
            engine.events.listen(event -> {
                if (event instanceof NpcDeleteEvent delete) {
                    delete.setCancelled(true);
                }
            });

            assertThat(engine.manager.delete(npc).join()).isFalse();
            assertThat(engine.manager.count()).isEqualTo(1);
            assertThat(npc.isRemoved()).isFalse();
        }

        @Test
        void invalidatesTheHandleAfterDeletion() {
            NpcHandle npc = create("shopkeeper");
            engine.manager.delete(npc).join();

            // An extension holding a deleted NPC is a real situation. Failing at the point of misuse
            // beats silently mutating something that no longer exists.
            assertThatThrownBy(() -> npc.setDisplayName("<green>Gone"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void freesTheNameAfterDeletion() {
            NpcHandle npc = create("shopkeeper");
            engine.manager.delete(npc).join();

            assertThat(engine.manager.exists("shopkeeper")).isFalse();
            assertThat(create("shopkeeper")).isNotNull();
        }
    }

    @Nested
    @DisplayName("spawning")
    class Spawning {

        @Test
        void allocatesEntityIdsAndIndexesTheNpc() {
            NpcHandle npc = create("shopkeeper");

            assertThat(npc.spawn()).isTrue();

            assertThat(npc.state()).isEqualTo(NpcState.SPAWNED);
            assertThat(npc.renderState().entityId()).isNotEqualTo(NpcRenderState.NO_ENTITY_ID);
            assertThat(engine.registry.byEntityId(npc.renderState().entityId())).contains(npc);
            assertThat(engine.index.size()).isEqualTo(1);
        }

        @Test
        void doesNotSendAnythingUntilTheTrackerRuns() {
            NpcHandle npc = create("shopkeeper");
            npc.spawn();

            // Spawning decides that the NPC is active; who can see it is the tracker's decision, and
            // duplicating that here is how the two would drift apart.
            assertThat(engine.adapter.calls()).isEmpty();
        }

        @Test
        void refusesToSpawnWhenAListenerCancels() {
            NpcHandle npc = create("shopkeeper");
            engine.events.listen(event -> {
                if (event instanceof NpcSpawnEvent spawn) {
                    spawn.setCancelled(true);
                }
            });

            assertThat(npc.spawn()).isFalse();
            assertThat(npc.isSpawned()).isFalse();
        }

        @Test
        void releasesEverythingOnDespawn() {
            NpcHandle npc = create("shopkeeper");
            npc.spawn();
            int entityId = npc.renderState().entityId();

            assertThat(npc.despawn()).isTrue();

            assertThat(npc.state()).isEqualTo(NpcState.DESPAWNED);
            assertThat(npc.renderState().entityId()).isEqualTo(NpcRenderState.NO_ENTITY_ID);
            assertThat(engine.registry.byEntityId(entityId)).isEmpty();
            assertThat(engine.index.size()).isZero();
            assertThat(engine.events.count(NpcDespawnEvent.class)).isEqualTo(1);
        }

        @Test
        void reportsDespawningAnAlreadyDespawnedNpcAsNoChange() {
            NpcHandle npc = create("shopkeeper");

            assertThat(npc.despawn()).isFalse();
            assertThat(engine.events.count(NpcDespawnEvent.class)).isZero();
        }
    }

    @Nested
    @DisplayName("visibility tracking")
    class VisibilityTracking {

        @Test
        void showsAnNpcToANearbyPlayer() {
            NpcHandle npc = create("shopkeeper");
            npc.spawn();
            PlayerMock player = playerAt("Alice", 5, 64, 5);

            engine.tick();

            assertThat(engine.adapter.count("spawnNpc")).isEqualTo(1);
            assertThat(npc.isVisibleTo(player)).isTrue();
            assertThat(npc.viewers()).containsExactly(player);
        }

        @Test
        void doesNotShowAnNpcToADistantPlayer() {
            NpcHandle npc = create("shopkeeper");
            npc.spawn();
            playerAt("Alice", 500, 64, 500);

            engine.tick();

            assertThat(engine.adapter.count("spawnNpc")).isZero();
        }

        @Test
        void hidesTheNpcWhenThePlayerWalksAway() {
            NpcHandle npc = create("shopkeeper");
            npc.spawn();
            PlayerMock player = playerAt("Alice", 5, 64, 5);
            engine.tick();
            engine.adapter.clear();

            player.setLocation(new Location(world, 500, 64, 500));
            engine.tick();

            assertThat(engine.adapter.count("removeEntities")).isEqualTo(1);
            assertThat(npc.isVisibleTo(player)).isFalse();
        }

        @Test
        void doesNotResendAnNpcThePlayerAlreadySees() {
            NpcHandle npc = create("shopkeeper");
            npc.spawn();
            playerAt("Alice", 5, 64, 5);

            engine.tick();
            engine.adapter.clear();
            engine.tick();
            engine.tick();

            // The tracker runs several times a second. Re-sending a spawn each pass would be the
            // single most expensive bug this class could have.
            assertThat(engine.adapter.count("spawnNpc")).isZero();
        }

        @Test
        void doesNotShowAnNpcInAnotherWorld() {
            server.addSimpleWorld("nether");
            NpcHandle npc = create("shopkeeper");
            npc.teleport(NpcPosition.of("nether", 0, 64, 0));
            npc.spawn();
            playerAt("Alice", 0, 64, 0);

            engine.tick();

            assertThat(engine.adapter.count("spawnNpc")).isZero();
        }

        @Test
        void respectsAVisibilityPermission() {
            NpcHandle npc = create("shopkeeper");
            npc.setVisibility(NpcVisibility.DEFAULT.withPermission("betternpcs.see.shop"));
            npc.spawn();
            PlayerMock player = playerAt("Alice", 5, 64, 5);

            engine.tick();
            assertThat(engine.adapter.count("spawnNpc")).isZero();

            player.addAttachment(MockBukkit.createMockPlugin(), "betternpcs.see.shop", true);
            engine.tick();
            assertThat(engine.adapter.count("spawnNpc")).isEqualTo(1);
        }

        @Test
        void hidesAnOptInNpcUntilItIsShownExplicitly() {
            NpcHandle npc = create("questgiver");
            npc.setVisibility(NpcVisibility.DEFAULT.withVisibleByDefault(false));
            npc.spawn();
            PlayerMock player = playerAt("Alice", 5, 64, 5);

            engine.tick();
            assertThat(engine.adapter.count("spawnNpc")).isZero();

            npc.show(player);
            engine.tick();
            assertThat(engine.adapter.count("spawnNpc")).isEqualTo(1);
        }

        @Test
        void keepsDistanceApplyingToAnExplicitlyShownNpc() {
            NpcHandle npc = create("questgiver");
            npc.setVisibility(NpcVisibility.DEFAULT.withVisibleByDefault(false));
            npc.spawn();
            PlayerMock player = playerAt("Alice", 500, 64, 500);

            npc.show(player);
            engine.tick();

            // "Show this NPC to that player" means "when they are near it", not "render it from
            // across the world".
            assertThat(engine.adapter.count("spawnNpc")).isZero();
        }

        @Test
        void lettingARuleRefuseBeatsAnExplicitShow() {
            NpcHandle npc = create("shopkeeper");
            npc.spawn();
            PlayerMock player = playerAt("Alice", 5, 64, 5);
            engine.manager.registerVisibilityRule((candidate, viewer) -> false);

            npc.show(player);
            engine.tick();

            assertThat(engine.adapter.count("spawnNpc")).isZero();
        }

        @Test
        void anExplicitHideBeatsEverything() {
            NpcHandle npc = create("shopkeeper");
            npc.spawn();
            PlayerMock player = playerAt("Alice", 5, 64, 5);

            npc.hide(player);
            engine.tick();

            assertThat(engine.adapter.count("spawnNpc")).isZero();
        }

        @Test
        void capsHowManyNpcsOnePlayerIsSent() {
            for (int i = 0; i < 10; i++) {
                NpcHandle npc = (NpcHandle) engine.manager.create(
                        "npc" + i, NpcType.PLAYER, NpcPosition.of("world", i, 64, 0));
                npc.spawn();
            }
            playerAt("Alice", 0, 64, 0);

            engine.config(withMaxTracked(3));
            engine.tick();

            assertThat(engine.adapter.count("spawnNpc")).isEqualTo(3);
        }

        @Test
        void keepsTheNearestNpcsWhenCapping() {
            NpcHandle near = (NpcHandle) engine.manager.create(
                    "near", NpcType.PLAYER, NpcPosition.of("world", 1, 64, 0));
            NpcHandle far = (NpcHandle) engine.manager.create(
                    "far", NpcType.PLAYER, NpcPosition.of("world", 20, 64, 0));
            near.spawn();
            far.spawn();
            playerAt("Alice", 0, 64, 0);

            engine.config(withMaxTracked(1));
            engine.tick();

            assertThat(near.viewers()).hasSize(1);
            assertThat(far.viewers()).isEmpty();
        }

        @Test
        void forgetsAPlayerWhoLeaves() {
            NpcHandle npc = create("shopkeeper");
            npc.spawn();
            PlayerMock player = playerAt("Alice", 5, 64, 5);
            engine.tick();

            engine.tracker.forget(player);

            assertThat(npc.isVisibleTo(player)).isFalse();
            assertThat(engine.tracker.viewedBy(player)).isEmpty();
        }
    }

    @Nested
    @DisplayName("property updates")
    class PropertyUpdates {

        private NpcHandle npc;

        @BeforeEach
        void spawnForOneViewer() {
            npc = create("shopkeeper");
            npc.spawn();
            playerAt("Alice", 5, 64, 5);
            engine.tick();
            engine.adapter.clear();
        }

        @Test
        void sendsOneMetadataPacketForAnAppearanceChange() {
            npc.setAppearance(NpcAppearance.builder().glowing(true).build());

            assertThat(engine.adapter.kinds()).containsExactly("updateMetadata");
        }

        @Test
        void sendsNothingWhenTheAppearanceIsUnchanged() {
            npc.setAppearance(npc.appearance());

            assertThat(engine.adapter.calls()).isEmpty();
        }

        @Test
        void sendsATeleportForAMoveWithinAWorld() {
            npc.teleport(NpcPosition.of("world", 6, 64, 6));

            assertThat(engine.adapter.kinds()).contains("teleport");
            assertThat(engine.adapter.count("spawnNpc")).isZero();
        }

        @Test
        void respawnsForAMoveToAnotherWorld() {
            server.addSimpleWorld("nether");

            npc.teleport(NpcPosition.of("nether", 0, 64, 0));

            // The client has no concept of an entity crossing dimensions, so this has to be a
            // removal followed by a fresh spawn wherever the NPC now is.
            assertThat(engine.adapter.count("removeEntities")).isEqualTo(1);
            assertThat(npc.viewers()).isEmpty();
        }

        @Test
        void respawnsWhenTheTypeChanges() {
            npc.setType(NpcType.of(EntityType.VILLAGER));

            assertThat(engine.adapter.count("removeEntities")).isEqualTo(1);
            assertThat(engine.adapter.count("spawnNpc")).isEqualTo(1);
        }

        @Test
        void allocatesFreshEntityIdsOnRespawn() {
            int before = npc.renderState().entityId();

            npc.setType(NpcType.of(EntityType.VILLAGER));

            // Reusing an id would ask the client to accept a different entity under an id it already
            // has, which several client versions handle by rendering neither.
            assertThat(npc.renderState().entityId()).isNotEqualTo(before);
            assertThat(engine.registry.byEntityId(before)).isEmpty();
            assertThat(engine.registry.byEntityId(npc.renderState().entityId())).contains(npc);
        }

        @Test
        void sendsEquipmentWhenASlotChanges() {
            npc.setEquipment(org.bukkit.inventory.EquipmentSlot.HEAD,
                    new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLDEN_HELMET));

            assertThat(engine.adapter.kinds()).containsExactly("updateEquipment");
        }

        @Test
        void movesTheTextLinesWithTheNpc() {
            npc.setHologram(HologramSettings.of("<yellow>Shop"));
            engine.adapter.clear();

            npc.teleport(NpcPosition.of("world", 6, 64, 6));

            // Text lines are separate entities and do not follow the NPC on their own.
            assertThat(engine.adapter.count("updateText")).isPositive();
        }

        @Test
        void respawnsTheTextEntitiesWhenTheLineCountChanges() {
            npc.setHologram(HologramSettings.of("<yellow>One", "<gray>Two"));

            assertThat(npc.renderState().hologramIds()).hasSize(2);
            assertThat(engine.adapter.count("spawnText")).isPositive();
        }

        @Test
        void marksTheNpcDirtyForEveryChange() {
            npc.markClean();

            npc.setDisplayName("<green>Shop");

            assertThat(npc.isDirty()).isTrue();
        }

        @Test
        void doesNotMarkTheNpcDirtyForANoOpChange() {
            npc.setDisplayName("<green>Shop");
            npc.markClean();

            npc.setAppearance(npc.appearance());

            assertThat(npc.isDirty()).isFalse();
        }
    }

    @Nested
    @DisplayName("look tracking")
    class LookTracking {

        @Test
        void turnsTheNpcTowardsItsViewer() {
            NpcHandle npc = create("shopkeeper");
            npc.setLook(LookSettings.lookAtViewer());
            npc.spawn();
            playerAt("Alice", 10, 64, 0);

            engine.tick();

            // Sent on the same pass that first shows the NPC, so it is already facing the player
            // when it appears rather than snapping round a fraction of a second later.
            assertThat(engine.adapter.count("rotate")).isEqualTo(1);
            assertThat(engine.adapter.calls("rotate").get(0).detail()).contains("yaw=-90");
        }

        @Test
        void doesNotResendARotationThatHasNotChanged() {
            NpcHandle npc = create("shopkeeper");
            npc.setLook(LookSettings.lookAtViewer());
            npc.spawn();
            playerAt("Alice", 10, 64, 0);

            engine.tick();
            engine.tick();
            engine.tick();
            engine.tick();

            // A standing player produces the same rotation on every pass. Sending it each time is
            // exactly the wasted traffic look tracking is accused of.
            assertThat(engine.adapter.count("rotate")).isEqualTo(1);
        }

        @Test
        void sendsAFreshRotationWhenThePlayerMoves() {
            NpcHandle npc = create("shopkeeper");
            npc.setLook(LookSettings.lookAtViewer());
            npc.spawn();
            PlayerMock player = playerAt("Alice", 10, 64, 0);
            engine.tick();
            engine.adapter.clear();

            player.setLocation(new Location(world, 0, 64, 10));
            engine.tick();

            assertThat(engine.adapter.count("rotate")).isEqualTo(1);
        }

        @Test
        void doesNothingForAnNpcThatDoesNotLookAround() {
            NpcHandle npc = create("shopkeeper");
            npc.spawn();
            playerAt("Alice", 10, 64, 0);
            engine.tick();
            engine.adapter.clear();

            engine.tick();

            assertThat(engine.adapter.count("rotate")).isZero();
        }

        @Test
        void ignoresAPlayerBeyondTheLookRange() {
            NpcHandle npc = create("shopkeeper");
            npc.setLook(LookSettings.builder().mode(LookMode.LOOK_AT_VIEWER).range(4).build());
            npc.spawn();
            playerAt("Alice", 20, 64, 0);
            engine.tick();
            engine.adapter.clear();

            engine.tick();

            assertThat(engine.adapter.count("rotate")).isZero();
        }

        @Test
        void facesAPlayerDueEast() {
            float[] rotation = NpcTracker.rotationTowards(
                    NpcPosition.of("world", 0, 64, 0), new Location(world, 10, 64, 0));

            // In Minecraft, yaw -90 faces positive X.
            assertThat(rotation[0]).isCloseTo(-90.0f, org.assertj.core.data.Offset.offset(0.01f));
            assertThat(rotation[1]).isCloseTo(0.0f, org.assertj.core.data.Offset.offset(0.01f));
        }

        @Test
        void looksDownAtAPlayerBelowIt() {
            float[] rotation = NpcTracker.rotationTowards(
                    NpcPosition.of("world", 0, 64, 0), new Location(world, 0, 54, 10));

            assertThat(rotation[1]).isPositive();
        }
    }

    @Nested
    @DisplayName("actions")
    class Actions {

        @Test
        void refusesAnActionNoHandlerServes() {
            NpcHandle npc = create("shopkeeper");

            assertThatThrownBy(() -> npc.addAction(
                    InteractionType.RIGHT_CLICK, ActionDefinition.parse("nosuchaction: x")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nosuchaction");
        }

        @Test
        void refusesAnArgumentTheHandlerRejects() {
            NpcHandle npc = create("shopkeeper");

            assertThatThrownBy(() -> npc.addAction(
                    InteractionType.RIGHT_CLICK, ActionDefinition.parse("cooldown: soon")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void keepsActionsInTheOrderTheyWereAdded() {
            NpcHandle npc = create("shopkeeper");
            npc.addAction(InteractionType.RIGHT_CLICK, ActionDefinition.parse("message: one"));
            npc.addAction(InteractionType.RIGHT_CLICK, ActionDefinition.parse("message: two"));

            assertThat(npc.actions(InteractionType.RIGHT_CLICK))
                    .extracting(ActionDefinition::argument)
                    .containsExactly("one", "two");
        }

        @Test
        void runsAChainForAPlayer() {
            NpcHandle npc = create("shopkeeper");
            npc.addAction(InteractionType.RIGHT_CLICK, ActionDefinition.parse("message: <green>Hello"));
            PlayerMock player = playerAt("Alice", 5, 64, 5);

            npc.runActions(player, InteractionType.RIGHT_CLICK);

            // Compared as plain text: the message legitimately carries the colour the MiniMessage
            // source asked for, and asserting on the whole component would make every test brittle
            // to a formatting change.
            assertThat(plainNextMessage(player)).isEqualTo("Hello");
        }

        @Test
        void stopsTheChainWhenAPermissionIsMissing() {
            NpcHandle npc = create("shopkeeper");
            npc.addAction(InteractionType.RIGHT_CLICK,
                    ActionDefinition.parse("require-permission: shop.use"));
            npc.addAction(InteractionType.RIGHT_CLICK, ActionDefinition.parse("message: secret"));
            PlayerMock player = playerAt("Alice", 5, 64, 5);

            npc.runActions(player, InteractionType.RIGHT_CLICK);

            assertThat(player.nextComponentMessage()).isNull();
        }

        @Test
        void removesAnActionByIndex() {
            NpcHandle npc = create("shopkeeper");
            npc.addAction(InteractionType.RIGHT_CLICK, ActionDefinition.parse("message: one"));
            npc.addAction(InteractionType.RIGHT_CLICK, ActionDefinition.parse("message: two"));

            assertThat(npc.removeAction(InteractionType.RIGHT_CLICK, 0).argument()).isEqualTo("one");
            assertThat(npc.actions(InteractionType.RIGHT_CLICK)).hasSize(1);
        }

        @Test
        void reportsAnIndexThatDoesNotExist() {
            NpcHandle npc = create("shopkeeper");

            assertThatThrownBy(() -> npc.removeAction(InteractionType.RIGHT_CLICK, 0))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @Nested
    @DisplayName("persistence")
    class Persistence {

        @Test
        void writesOnlyTheNpcsThatChanged() {
            NpcHandle first = create("one");
            NpcHandle second = create("two");
            engine.manager.saveAll().join();
            int writesAfterFirstSave = engine.repository.saveCount();

            first.setDisplayName("<green>Changed");
            engine.manager.saveAll().join();

            assertThat(engine.repository.saveCount()).isEqualTo(writesAfterFirstSave + 1);
            assertThat(second.isDirty()).isFalse();
        }

        @Test
        void writesNothingWhenNothingChanged() {
            create("one");
            engine.manager.saveAll().join();
            int writes = engine.repository.saveCount();

            assertThat(engine.manager.saveAll().join()).isZero();
            assertThat(engine.repository.saveCount()).isEqualTo(writes);
        }

        @Test
        void roundTripsAnNpcThroughStorage() {
            NpcHandle original = create("shopkeeper");
            original.setDisplayName("<green>Shopkeeper");
            original.setNametag(NametagSettings.of("<gray>[Shop]"));
            original.setHologram(HologramSettings.of("<yellow>Open"));
            original.setAppearance(NpcAppearance.builder().glowing(true).build());
            original.setVisibility(NpcVisibility.withinDistance(24));
            original.addAction(InteractionType.RIGHT_CLICK, ActionDefinition.parse("message: hi"));
            original.setMetadata("shop:id", "general");
            engine.manager.saveAll().join();

            engine.manager.reload().join();

            Npc loaded = engine.manager.byName("shopkeeper").orElseThrow();
            assertThat(loaded.snapshot()).isEqualTo(original.snapshot());
        }

        @Test
        void invalidatesEveryHandleOnReload() {
            NpcHandle original = create("shopkeeper");
            engine.manager.saveAll().join();

            engine.manager.reload().join();

            // Documented on the API: extensions must look their NPCs up again after a reload.
            assertThat(original.isRemoved()).isTrue();
            assertThat(engine.manager.byName("shopkeeper")).isNotEmpty();
        }

        @Test
        void skipsAnUnloadableNpcWithoutLosingTheRest() {
            NpcHandle good = create("good");
            engine.manager.saveAll().join();

            // A duplicate name, as a hand-edited database could produce.
            engine.repository.put(good.snapshot().toBuilder()
                    .name("good")
                    .build()
                    .toBuilder()
                    .build());
            engine.repository.put(dev.shvquu.betternpcs.api.npc.NpcSnapshot
                    .builder(java.util.UUID.randomUUID(), "good", NpcType.PLAYER, origin())
                    .build());

            int loaded = engine.manager.reload().join();

            assertThat(loaded).isEqualTo(1);
            assertThat(engine.manager.count()).isEqualTo(1);
        }

        @Test
        void leavesTheNpcDirtyWhenASaveIsCancelled() {
            NpcHandle npc = create("shopkeeper");
            engine.events.listen(event -> {
                if (event instanceof dev.shvquu.betternpcs.api.event.NpcSaveEvent save) {
                    save.setCancelled(true);
                }
            });

            npc.save().join();

            // Cancelling skips this write, not the change. The next cycle must try again.
            assertThat(npc.isDirty()).isTrue();
            assertThat(engine.repository.size()).isZero();
        }
    }

    @Nested
    @DisplayName("renaming")
    class Renaming {

        @Test
        void movesTheNameIndex() {
            NpcHandle npc = create("old");

            npc.rename("new");

            assertThat(engine.manager.byName("old")).isEmpty();
            assertThat(engine.manager.byName("new")).contains(npc);
        }

        @Test
        void refusesANameAnotherNpcHas() {
            create("taken");
            NpcHandle npc = create("mine");

            assertThatThrownBy(() -> npc.rename("taken"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(npc.name()).isEqualTo("mine");
        }

        @Test
        void allowsAChangeOfCapitalisation() {
            NpcHandle npc = create("shopkeeper");

            npc.rename("ShopKeeper");

            assertThat(npc.name()).isEqualTo("ShopKeeper");
            assertThat(engine.manager.byName("SHOPKEEPER")).contains(npc);
        }
    }

    private dev.shvquu.betternpcs.core.config.BetterNpcsConfig withMaxTracked(int maxTracked) {
        dev.shvquu.betternpcs.core.config.BetterNpcsConfig defaults =
                dev.shvquu.betternpcs.core.config.BetterNpcsConfig.defaults();
        dev.shvquu.betternpcs.core.config.BetterNpcsConfig.Npc npc = defaults.npc();
        return new dev.shvquu.betternpcs.core.config.BetterNpcsConfig(
                defaults.plugin(),
                defaults.storage(),
                new dev.shvquu.betternpcs.core.config.BetterNpcsConfig.Npc(
                        npc.defaultViewDistance(),
                        npc.trackerInterval(),
                        npc.saveInterval(),
                        npc.interactionCooldown(),
                        npc.cacheSkins(),
                        npc.skinCacheDuration(),
                        npc.skinRequestTimeout(),
                        npc.usePackets(),
                        maxTracked),
                defaults.language(),
                defaults.updates());
    }
}
