package dev.shvquu.betternpcs.core.backup;

import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import dev.shvquu.betternpcs.core.npc.DefaultNpcManager;
import dev.shvquu.betternpcs.core.npc.NpcHandle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Owns the backups folder: writes backups into it, lists what is in it, and turns a name typed at
 * the console into a path that is certainly inside it.
 *
 * <h2>The folder is a boundary</h2>
 *
 * <p>Every path this class hands out is built from a name that passed {@link
 * BackupFile#isValidName(String)} and is then checked again after resolving, so a name cannot reach
 * a file outside the backups folder however it is spelled. The command layer never builds a path
 * itself.
 *
 * <h2>Threading</h2>
 *
 * <p>Nothing here touches Bukkit state except {@link #snapshotEverything(DefaultNpcManager)}, which
 * must run on the main thread. Reading and writing the files is blocking I/O and belongs on an
 * async task; the two are separate methods so a caller can put each where it goes.
 *
 * @since 1.0.0
 */
public final class BackupService {

    private final Path directory;
    private final String pluginVersion;

    /**
     * Creates the service.
     *
     * @param directory     the backups folder, usually {@code plugins/BetterNPCs/backups}
     * @param pluginVersion the version recorded in files this service writes
     * @throws NullPointerException if either argument is {@code null}
     */
    public BackupService(Path directory, String pluginVersion) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.pluginVersion = Objects.requireNonNull(pluginVersion, "pluginVersion");
    }

    /**
     * Returns the backups folder.
     *
     * @return the folder, absolute and normalised
     */
    public Path directory() {
        return directory;
    }

    /**
     * Takes a snapshot of every NPC currently loaded.
     *
     * <p>Must be called on the main server thread — it reads live NPC state. The result is immutable
     * and safe to hand to an async task for writing.
     *
     * @param manager the manager holding the NPCs
     * @return the snapshots
     * @throws NullPointerException if {@code manager} is {@code null}
     */
    public static List<NpcSnapshot> snapshotEverything(DefaultNpcManager manager) {
        Objects.requireNonNull(manager, "manager");
        List<NpcSnapshot> snapshots = new ArrayList<>();
        for (NpcHandle handle : manager.handles()) {
            snapshots.add(handle.snapshot());
        }
        return List.copyOf(snapshots);
    }

    /**
     * Writes a backup.
     *
     * <p>Blocking I/O — call from an async task.
     *
     * @param name      the file name without extension, or {@code null} for a timestamp
     * @param snapshots what to back up, from {@link #snapshotEverything(DefaultNpcManager)}
     * @return the file that was written
     * @throws IllegalArgumentException if {@code name} is not a usable file name
     * @throws IOException              if the file could not be written
     * @throws NullPointerException     if {@code snapshots} is {@code null}
     */
    public Path write(String name, List<NpcSnapshot> snapshots) throws IOException {
        Objects.requireNonNull(snapshots, "snapshots");

        Path target = name == null
                ? directory.resolve(BackupFile.timestampedName(Instant.now()))
                : resolve(name);

        BackupFile.of(pluginVersion, snapshots).writeTo(target);
        return target;
    }

    /**
     * Reads a backup by name.
     *
     * <p>Blocking I/O — call from an async task.
     *
     * @param name the file name, with or without the {@code .json} extension
     * @return the backup
     * @throws IllegalArgumentException if the name is unusable, or the file is not a backup
     * @throws java.nio.file.NoSuchFileException if there is no such backup
     * @throws IOException              if the file could not be read
     * @throws NullPointerException     if {@code name} is {@code null}
     */
    public BackupFile read(String name) throws IOException {
        return BackupFile.readFrom(resolve(name));
    }

    /**
     * Lists the backups in the folder, newest file first.
     *
     * <p>Blocking I/O — call from an async task. The contents are not read, only the names, so a
     * corrupt file still appears in the list and reports its problem when restored.
     *
     * @return the file names, including the extension
     * @throws IOException if the folder could not be read
     */
    public List<String> list() throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(BackupFile.EXTENSION))
                    .sorted(Comparator.comparing(BackupService::modifiedAt).reversed())
                    .map(path -> path.getFileName().toString())
                    .toList();
        }
    }

    private static Instant modifiedAt(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException unreadable) {
            // Sorting is a convenience. A file whose timestamp cannot be read sorts last rather than
            // taking the whole listing down with it.
            return Instant.EPOCH;
        }
    }

    /**
     * Turns a backup name into a path inside the backups folder.
     *
     * @param name the file name, with or without the {@code .json} extension
     * @return the path
     * @throws IllegalArgumentException if the name could reach outside the folder
     * @throws NullPointerException     if {@code name} is {@code null}
     */
    public Path resolve(String name) {
        Objects.requireNonNull(name, "name");

        String bare = name.endsWith(BackupFile.EXTENSION)
                ? name.substring(0, name.length() - BackupFile.EXTENSION.length())
                : name;

        if (!BackupFile.isValidName(bare)) {
            throw new IllegalArgumentException("'" + name + "' is not a usable backup name");
        }

        Path resolved = directory.resolve(bare + BackupFile.EXTENSION).normalize();
        if (!resolved.startsWith(directory)) {
            // Belt and braces. isValidName should already have refused anything that could do this,
            // but a path check after resolving is what actually proves it.
            throw new IllegalArgumentException("'" + name + "' is not a usable backup name");
        }
        return resolved;
    }
}
