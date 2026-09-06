package dev.shvquu.betternpcs.core.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import dev.shvquu.betternpcs.api.npc.NpcType;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BackupServiceTest {

    @TempDir
    Path root;

    private Path backupsFolder;
    private BackupService service;

    @BeforeEach
    void setUp() {
        backupsFolder = root.resolve("backups");
        service = new BackupService(backupsFolder, "1.0.0");
    }

    private static List<NpcSnapshot> oneNpc(String name) {
        return List.of(NpcSnapshot.builder(UUID.randomUUID(), name, NpcType.PLAYER,
                NpcPosition.of("world", 0, 64, 0)).build());
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a named backup lands under that name")
    void writesUnderTheGivenName() throws IOException {
        Path written = service.write("weekly", oneNpc("shopkeeper"));

        assertThat(written).isEqualTo(backupsFolder.resolve("weekly.json"));
        assertThat(service.read("weekly").npcs()).hasSize(1);
    }

    @Test
    @DisplayName("no name means a timestamp, so nothing is overwritten by accident")
    void writesUnderATimestampByDefault() throws IOException {
        Path written = service.write(null, oneNpc("shopkeeper"));

        assertThat(written.getFileName().toString()).endsWith(BackupFile.EXTENSION);
        assertThat(service.list()).containsExactly(written.getFileName().toString());
    }

    @Test
    @DisplayName("the extension is optional when reading")
    void readsWithOrWithoutTheExtension() throws IOException {
        service.write("weekly", oneNpc("shopkeeper"));

        assertThat(service.read("weekly.json").npcs()).hasSize(1);
        assertThat(service.read("weekly").npcs()).hasSize(1);
    }

    @Test
    @DisplayName("reading a backup that is not there says so specifically")
    void reportsAMissingBackup() {
        assertThatThrownBy(() -> service.read("never-taken"))
                .isInstanceOf(NoSuchFileException.class);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("listing a folder that does not exist yet is empty, not an error")
    void listsNothingBeforeTheFirstBackup() throws IOException {
        assertThat(service.list()).isEmpty();
    }

    @Test
    @DisplayName("the newest backup is listed first")
    void listsNewestFirst() throws IOException {
        service.write("older", oneNpc("a"));
        service.write("newer", oneNpc("b"));
        // Set explicitly: two writes a millisecond apart are not reliably ordered by the filesystem.
        Files.setLastModifiedTime(backupsFolder.resolve("older.json"),
                FileTime.from(Instant.parse("2026-01-01T00:00:00Z")));
        Files.setLastModifiedTime(backupsFolder.resolve("newer.json"),
                FileTime.from(Instant.parse("2026-06-01T00:00:00Z")));

        assertThat(service.list()).containsExactly("newer.json", "older.json");
    }

    @Test
    @DisplayName("files that are not backups are not listed")
    void ignoresOtherFiles() throws IOException {
        service.write("weekly", oneNpc("a"));
        Files.writeString(backupsFolder.resolve("notes.txt"), "hello");
        Files.createDirectory(backupsFolder.resolve("archive.json"));

        assertThat(service.list()).containsExactly("weekly.json");
    }

    // ---------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"../../secrets", "..", "sub/backup", "sub\\backup", "with space"})
    @DisplayName("a name cannot reach outside the backups folder")
    void refusesToResolveOutsideTheFolder(String name) {
        assertThatThrownBy(() -> service.resolve(name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a usable backup name");
    }

    @Test
    @DisplayName("every resolved path is inside the backups folder")
    void resolvesInsideTheFolder() {
        assertThat(service.resolve("weekly")).isEqualTo(backupsFolder.resolve("weekly.json"));
        // getParent() rather than hasParent(), which resolves symlinks and so needs the file to
        // exist — and the point here is that the path is safe before anything is written to it.
        assertThat(service.resolve("weekly.json").getParent()).isEqualTo(service.directory());
    }

    @Test
    @DisplayName("writing refuses an unusable name rather than putting the file somewhere else")
    void refusesToWriteOutsideTheFolder() {
        assertThatThrownBy(() -> service.write("../escape", oneNpc("a")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(root.resolve("escape.json")).doesNotExist();
    }
}
