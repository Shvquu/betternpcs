package dev.shvquu.betternpcs.core.backup;

import static org.assertj.core.api.Assertions.assertThat;

import dev.shvquu.betternpcs.api.action.ActionDefinition;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.api.npc.Npc;
import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import dev.shvquu.betternpcs.api.npc.NpcType;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.core.engine.EngineHarness;
import dev.shvquu.betternpcs.core.npc.NpcHandle;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class RestoreServiceTest {

    @TempDir
    Path languagesFolder;

    private ServerMock server;
    private EngineHarness engine;
    private RestoreService restores;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        engine = new EngineHarness(languagesFolder);

        Logger logger = Logger.getLogger("RestoreServiceTest");
        logger.setLevel(Level.OFF);
        restores = new RestoreService(engine.manager, logger);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private NpcHandle create(String name) {
        return (NpcHandle) engine.manager.create(name, NpcType.PLAYER,
                NpcPosition.of("world", 0, 64, 0));
    }

    private static BackupFile backupOf(NpcSnapshot... snapshots) {
        return BackupFile.of("1.0.0", List.of(snapshots));
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("everything in the file is created on an empty server")
    void createsWhatIsMissing() {
        NpcSnapshot away = create("shopkeeper").snapshot();
        engine.manager.delete(engine.manager.byName("shopkeeper").orElseThrow()).join();

        RestoreService.Plan plan = restores.plan(backupOf(away));

        assertThat(plan.created()).containsExactly(away);
        assertThat(plan.overwritten()).isEmpty();
        assertThat(plan.blocked()).isEmpty();
        assertThat(plan.total()).isEqualTo(1);
        assertThat(plan.changesAnything(false)).isTrue();
    }

    @Test
    @DisplayName("planning changes nothing")
    void planningIsADryRun() {
        NpcSnapshot away = create("shopkeeper").snapshot();
        engine.manager.delete(engine.manager.byName("shopkeeper").orElseThrow()).join();

        restores.plan(backupOf(away));

        assertThat(engine.manager.count()).isZero();
    }

    @Test
    @DisplayName("applying restores every property, not just the name and position")
    void applyRestoresTheWholeNpc() {
        NpcHandle original = create("shopkeeper");
        original.setDisplayName("<gold>Shopkeeper");
        original.addAction(InteractionType.RIGHT_CLICK,
                ActionDefinition.parse("message: <green>Hello"));
        original.setMetadata("shop", "tools");
        NpcSnapshot away = original.snapshot();

        engine.manager.delete(original).join();
        RestoreService.Result result = restores.apply(restores.plan(backupOf(away)), false);

        assertThat(result).isEqualTo(new RestoreService.Result(1, 0, 0));
        Npc restored = engine.manager.byName("shopkeeper").orElseThrow();
        // The same unique id, so anything holding a reference to this NPC keeps resolving.
        assertThat(restored.uniqueId()).isEqualTo(away.uniqueId());
        assertThat(restored.snapshot()).isEqualTo(away);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("an NPC that still exists is left alone by default")
    void mergesRatherThanReplacing() {
        NpcHandle live = create("shopkeeper");
        NpcSnapshot stale = live.snapshot().toBuilder().displayName("<red>Old").build();

        RestoreService.Plan plan = restores.plan(backupOf(stale));
        assertThat(plan.overwritten()).containsExactly(stale);
        assertThat(plan.created()).isEmpty();
        assertThat(plan.changesAnything(false)).as("a merge with nothing new does nothing").isFalse();

        RestoreService.Result result = restores.apply(plan, false);

        assertThat(result).isEqualTo(new RestoreService.Result(0, 0, 0));
        assertThat(live.displayName()).isEmpty();
    }

    @Test
    @DisplayName("--replace overwrites the NPC that shares the id")
    void replaceOverwritesTheSameNpc() {
        NpcHandle live = create("shopkeeper");
        NpcSnapshot stored = live.snapshot().toBuilder().displayName("<red>From the backup").build();

        RestoreService.Result result = restores.apply(restores.plan(backupOf(stored)), true);

        assertThat(result).isEqualTo(new RestoreService.Result(0, 1, 0));
        assertThat(live.displayName()).contains("<red>From the backup");
    }

    @Test
    @DisplayName("an NPC missing from the backup is never deleted, even with --replace")
    void neverDeletes() {
        create("keep-me");
        NpcSnapshot other = NpcSnapshot.builder(UUID.randomUUID(), "restored", NpcType.PLAYER,
                NpcPosition.of("world", 0, 64, 0)).build();

        restores.apply(restores.plan(backupOf(other)), true);

        assertThat(engine.manager.byName("keep-me")).isPresent();
        assertThat(engine.manager.count()).isEqualTo(2);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a different NPC holding the name blocks the entry rather than displacing it")
    void refusesToTakeANameFromAnotherNpc() {
        NpcHandle live = create("shopkeeper");
        NpcSnapshot impostor = NpcSnapshot.builder(UUID.randomUUID(), "shopkeeper", NpcType.PLAYER,
                NpcPosition.of("world", 0, 64, 0)).build();

        RestoreService.Plan plan = restores.plan(backupOf(impostor));

        assertThat(plan.blocked())
                .containsExactly(new RestoreService.Blocked(impostor, RestoreService.Conflict.NAME_TAKEN));

        restores.apply(plan, true);

        assertThat(engine.manager.byName("shopkeeper")).contains(live);
        assertThat(engine.manager.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a hand-edited file with an impossible name reports that, not a clash")
    void blocksAnUnusableName() {
        NpcSnapshot broken = NpcSnapshot.builder(UUID.randomUUID(), "two words", NpcType.PLAYER,
                NpcPosition.of("world", 0, 64, 0)).build();

        RestoreService.Plan plan = restores.plan(backupOf(broken));

        assertThat(plan.blocked())
                .extracting(RestoreService.Blocked::conflict)
                .containsExactly(RestoreService.Conflict.UNUSABLE);
        assertThat(plan.created()).isEmpty();
    }

    @Test
    @DisplayName("one bad entry does not abort the rest")
    void keepsGoingPastAFailure() {
        NpcSnapshot good = NpcSnapshot.builder(UUID.randomUUID(), "good", NpcType.PLAYER,
                NpcPosition.of("world", 0, 64, 0)).build();
        NpcSnapshot broken = NpcSnapshot.builder(UUID.randomUUID(), "two words", NpcType.PLAYER,
                NpcPosition.of("world", 0, 64, 0)).build();

        RestoreService.Plan plan = restores.plan(backupOf(broken, good));
        RestoreService.Result result = restores.apply(plan, false);

        assertThat(result.created()).isEqualTo(1);
        assertThat(engine.manager.byName("good")).isPresent();
    }

    @Test
    @DisplayName("an empty backup is a no-op")
    void handlesAnEmptyBackup() {
        create("shopkeeper");

        RestoreService.Plan plan = restores.plan(backupOf());

        assertThat(plan.total()).isZero();
        assertThat(plan.changesAnything(true)).isFalse();
        assertThat(restores.apply(plan, true)).isEqualTo(new RestoreService.Result(0, 0, 0));
        assertThat(engine.manager.count()).isEqualTo(1);
    }
}
