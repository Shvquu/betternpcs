package dev.shvquu.betternpcs.core.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.shvquu.betternpcs.api.action.ActionDefinition;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import dev.shvquu.betternpcs.api.npc.NpcType;
import dev.shvquu.betternpcs.api.npc.property.LookSettings;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BackupFileTest {

    @TempDir
    Path folder;

    private static NpcSnapshot snapshot(String name) {
        return NpcSnapshot.builder(UUID.randomUUID(), name, NpcType.PLAYER,
                        NpcPosition.of("world", 1.5, 64.0, -2.5))
                .displayName("<gold>Shopkeeper")
                .look(LookSettings.lookAtViewer())
                .actions(InteractionType.RIGHT_CLICK,
                        List.of(ActionDefinition.parse("message: <green>Hello")))
                .metadata("shop", "tools")
                .spawnByDefault(false)
                .build();
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a written backup reads back as the same NPCs")
    void roundTripsEveryProperty() throws IOException {
        NpcSnapshot original = snapshot("shopkeeper");
        Path file = folder.resolve("backup.json");

        BackupFile.of("1.0.0", List.of(original)).writeTo(file);
        BackupFile read = BackupFile.readFrom(file);

        assertThat(read.formatVersion()).isEqualTo(BackupFile.FORMAT_VERSION);
        assertThat(read.pluginVersion()).isEqualTo("1.0.0");
        // Not just the identity fields: equals() covers appearance, actions and metadata too, which
        // is the whole reason a backup is worth taking.
        assertThat(read.npcs()).containsExactly(original);
    }

    @Test
    @DisplayName("writing creates the folder it was pointed at")
    void createsParentDirectories() throws IOException {
        Path file = folder.resolve("nested").resolve("deeper").resolve("backup.json");

        BackupFile.of("1.0.0", List.of(snapshot("guard"))).writeTo(file);

        assertThat(file).exists();
    }

    @Test
    @DisplayName("writing over an existing backup leaves no temporary file behind")
    void replacesCleanly() throws IOException {
        Path file = folder.resolve("backup.json");
        BackupFile.of("1.0.0", List.of(snapshot("first"))).writeTo(file);
        BackupFile.of("1.0.0", List.of(snapshot("second"))).writeTo(file);

        assertThat(BackupFile.readFrom(file).npcs())
                .extracting(NpcSnapshot::name)
                .containsExactly("second");
        assertThat(folder.resolve("backup.json.tmp")).doesNotExist();
    }

    @Test
    @DisplayName("an empty backup is a valid backup")
    void handlesNoNpcs() throws IOException {
        Path file = folder.resolve("empty.json");
        BackupFile.of("1.0.0", List.of()).writeTo(file);

        assertThat(BackupFile.readFrom(file).npcs()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("some other JSON file is refused rather than half-read")
    void refusesSomethingThatIsNotABackup() {
        assertThatThrownBy(() -> BackupFile.read(new StringReader("{\"players\": []}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a BetterNPCs backup");
    }

    @Test
    @DisplayName("a file that is not JSON at all is refused")
    void refusesGarbage() {
        assertThatThrownBy(() -> BackupFile.read(new StringReader("not json")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a backup from a newer format is refused with the versions named")
    void refusesANewerFormat() {
        String newer = "{\"formatVersion\":999,\"npcs\":[]}";

        assertThatThrownBy(() -> BackupFile.read(new StringReader(newer)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999")
                .hasMessageContaining(String.valueOf(BackupFile.FORMAT_VERSION));
    }

    @Test
    @DisplayName("an unreadable timestamp does not cost the whole backup")
    void toleratesABadTimestamp() throws IOException {
        Path file = folder.resolve("backup.json");
        BackupFile.of("1.0.0", List.of(snapshot("guard"))).writeTo(file);
        String damaged = Files.readString(file, StandardCharsets.UTF_8)
                .replaceFirst("\"created\":\"[^\"]+\"", "\"created\":\"yesterday\"");
        Files.writeString(file, damaged, StandardCharsets.UTF_8);

        BackupFile read = BackupFile.readFrom(file);

        assertThat(read.created()).isEqualTo(Instant.EPOCH);
        assertThat(read.npcs()).hasSize(1);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the generated name is a legal file name and sorts by time")
    void timestampedNamesSortChronologically() {
        String earlier = BackupFile.timestampedName(Instant.parse("2026-01-02T03:04:05Z"));
        String later = BackupFile.timestampedName(Instant.parse("2026-01-02T03:04:06Z"));

        assertThat(earlier).endsWith(BackupFile.EXTENSION).doesNotContain(":");
        assertThat(BackupFile.isValidName(earlier.replace(BackupFile.EXTENSION, ""))).isTrue();
        assertThat(earlier).isLessThan(later);
    }

    @ParameterizedTest
    @ValueSource(strings = {"weekly", "before-1.2.0", "backup_2026", "a.b.c"})
    void acceptsPlainNames(String name) {
        assertThat(BackupFile.isValidName(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "../escape", "..", "sub/backup", "sub\\backup", "C:backup", "with space", "", "  "
    })
    @DisplayName("anything that could reach outside the folder is refused")
    void refusesUnsafeNames(String name) {
        assertThat(BackupFile.isValidName(name)).isFalse();
    }

    @Test
    void refusesANullName() {
        assertThat(BackupFile.isValidName(null)).isFalse();
    }
}
