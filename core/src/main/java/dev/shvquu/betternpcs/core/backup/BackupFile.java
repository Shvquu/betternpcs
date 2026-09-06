package dev.shvquu.betternpcs.core.backup;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.core.storage.SnapshotCodec;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reads and writes the JSON file {@code /npc backup} produces.
 *
 * <h2>The same document the database stores</h2>
 *
 * <p>Each NPC is the identity and position fields the storage backends keep as columns, plus the
 * JSON {@link SnapshotCodec} already produces for everything else. Reusing the codec means the
 * backup format and the storage format cannot drift apart, and it means a backup is
 * <em>backend-independent</em> — a file taken from SQLite restores into MongoDB without conversion.
 *
 * <p>Because the codec reads tolerantly, a backup taken before a new NPC property existed still
 * restores afterwards: the missing field takes its default. That is the property that makes a backup
 * worth taking at all, since the version you restore on is rarely the version you backed up from.
 *
 * <h2>What it does not contain</h2>
 *
 * <p>No configuration, no language files, no entity ids. Entity ids are allocated at spawn and
 * meaningless in a file; configuration is a text file the server owner already has.
 *
 * @since 1.0.0
 */
public final class BackupFile {

    /**
     * The format version written into every file.
     *
     * <p>Read on restore so that a future incompatible change can be recognised and refused with a
     * clear message rather than mis-parsed. Additive changes do not need a bump — the codec's
     * tolerant reads already cover them.
     */
    public static final int FORMAT_VERSION = 1;

    /** The file extension backups are written with. */
    public static final String EXTENSION = ".json";

    private final int formatVersion;
    private final Instant created;
    private final String pluginVersion;
    private final List<NpcSnapshot> npcs;

    private BackupFile(
            int formatVersion, Instant created, String pluginVersion, List<NpcSnapshot> npcs) {
        this.formatVersion = formatVersion;
        this.created = created;
        this.pluginVersion = pluginVersion;
        this.npcs = List.copyOf(npcs);
    }

    /**
     * Creates a backup of the given NPCs.
     *
     * @param pluginVersion the version taking the backup, recorded for diagnostics
     * @param npcs          the NPCs to include
     * @return the backup
     * @throws NullPointerException if either argument or any element is {@code null}
     */
    public static BackupFile of(String pluginVersion, Collection<NpcSnapshot> npcs) {
        Objects.requireNonNull(pluginVersion, "pluginVersion");
        Objects.requireNonNull(npcs, "npcs");

        List<NpcSnapshot> copy = new ArrayList<>(npcs.size());
        for (NpcSnapshot snapshot : npcs) {
            copy.add(Objects.requireNonNull(snapshot, "snapshot"));
        }
        return new BackupFile(FORMAT_VERSION, Instant.now(), pluginVersion, copy);
    }

    /**
     * Returns the format version this backup was written with.
     *
     * @return the format version
     */
    public int formatVersion() {
        return formatVersion;
    }

    /**
     * Returns when the backup was taken.
     *
     * @return the timestamp
     */
    public Instant created() {
        return created;
    }

    /**
     * Returns the plugin version that took the backup.
     *
     * @return the version string
     */
    public String pluginVersion() {
        return pluginVersion;
    }

    /**
     * Returns the NPCs in the backup.
     *
     * @return an immutable list
     */
    public List<NpcSnapshot> npcs() {
        return npcs;
    }

    // ---------------------------------------------------------------------------------------------
    // Writing
    // ---------------------------------------------------------------------------------------------

    /**
     * Writes the backup to a file, creating parent directories as needed.
     *
     * <p>Written to a temporary file and then moved into place, so that a failure part-way through
     * leaves the previous backup intact rather than a truncated file that looks like a backup and is
     * not one.
     *
     * @param target where to write
     * @throws NullPointerException if {@code target} is {@code null}
     * @throws IOException          if the file could not be written
     */
    public void writeTo(Path target) throws IOException {
        Objects.requireNonNull(target, "target");

        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            writer.write(toJson().toString());
        }

        try {
            Files.move(temporary, target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException notAtomic) {
            // Some filesystems, and some Windows configurations, cannot move atomically. A
            // non-atomic move is still better than writing in place.
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Returns the backup as a JSON document.
     *
     * @return the document
     */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", formatVersion);
        root.addProperty("created", created.toString());
        root.addProperty("pluginVersion", pluginVersion);

        JsonArray array = new JsonArray();
        for (NpcSnapshot snapshot : npcs) {
            array.add(write(snapshot));
        }
        root.add("npcs", array);
        return root;
    }

    private static JsonObject write(NpcSnapshot snapshot) {
        NpcPosition position = snapshot.position();

        JsonObject json = new JsonObject();
        json.addProperty("uuid", snapshot.uniqueId().toString());
        json.addProperty("name", snapshot.name());
        json.addProperty("type", snapshot.type().id());
        json.addProperty("world", position.world());
        json.addProperty("x", position.x());
        json.addProperty("y", position.y());
        json.addProperty("z", position.z());
        json.addProperty("yaw", position.yaw());
        json.addProperty("pitch", position.pitch());
        json.addProperty("spawnByDefault", snapshot.spawnByDefault());
        // Parsed rather than embedded as a string, so the file stays readable and editable by hand —
        // which is half the point of having a backup in a text format.
        json.add("data", JsonParser.parseString(SnapshotCodec.encode(snapshot)));
        return json;
    }

    // ---------------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------------

    /**
     * Reads a backup from a file.
     *
     * @param source the file to read
     * @return the backup
     * @throws NullPointerException     if {@code source} is {@code null}
     * @throws IOException              if the file could not be read
     * @throws IllegalArgumentException if it is not a BetterNPCs backup, or was written by a format
     *                                  version this build does not understand
     */
    public static BackupFile readFrom(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    /**
     * Reads a backup.
     *
     * @param reader the JSON to read
     * @return the backup
     * @throws NullPointerException     if {@code reader} is {@code null}
     * @throws IllegalArgumentException if it is not a BetterNPCs backup, or was written by a format
     *                                  version this build does not understand
     */
    public static BackupFile read(Reader reader) {
        Objects.requireNonNull(reader, "reader");

        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("That file is not a BetterNPCs backup");
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException malformed) {
            throw new IllegalArgumentException("That file is not valid JSON", malformed);
        }

        int version = root.has("formatVersion") ? root.get("formatVersion").getAsInt() : 0;
        if (version == 0 || !root.has("npcs")) {
            // Refused rather than guessed at. Reading an arbitrary JSON file as a backup would at
            // best fail confusingly and at worst restore nonsense over real NPCs.
            throw new IllegalArgumentException("That file is not a BetterNPCs backup");
        }
        if (version > FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "That backup was written by a newer version of BetterNPCs (format " + version
                            + ", this build understands " + FORMAT_VERSION + ")");
        }

        JsonElement npcArray = root.get("npcs");
        if (!npcArray.isJsonArray()) {
            throw new IllegalArgumentException("That file is not a BetterNPCs backup");
        }

        List<NpcSnapshot> snapshots = new ArrayList<>();
        for (JsonElement element : npcArray.getAsJsonArray()) {
            if (element.isJsonObject()) {
                snapshots.add(readSnapshot(element.getAsJsonObject()));
            }
        }

        Instant created = root.has("created")
                ? parseInstant(root.get("created").getAsString())
                : Instant.EPOCH;
        String pluginVersion = root.has("pluginVersion")
                ? root.get("pluginVersion").getAsString()
                : "unknown";

        return new BackupFile(version, created, pluginVersion, snapshots);
    }

    private static Instant parseInstant(String text) {
        try {
            return Instant.parse(text);
        } catch (RuntimeException unreadable) {
            // A timestamp is documentation, not data. An unreadable one is not worth refusing the
            // whole backup over.
            return Instant.EPOCH;
        }
    }

    private static NpcSnapshot readSnapshot(JsonObject json) {
        NpcPosition position = new NpcPosition(
                json.get("world").getAsString(),
                json.get("x").getAsDouble(),
                json.get("y").getAsDouble(),
                json.get("z").getAsDouble(),
                json.get("yaw").getAsFloat(),
                json.get("pitch").getAsFloat());

        JsonElement data = json.get("data");

        return SnapshotCodec.decode(
                UUID.fromString(json.get("uuid").getAsString()),
                json.get("name").getAsString(),
                json.get("type").getAsString(),
                position,
                !json.has("spawnByDefault") || json.get("spawnByDefault").getAsBoolean(),
                data == null || data.isJsonNull() ? null : data.toString());
    }

    /**
     * Returns the file name a backup taken now should be given.
     *
     * <p>Sortable and free of characters a filesystem objects to, so that a folder of them reads in
     * chronological order without anyone having to think about it.
     *
     * @param at the moment the backup is taken
     * @return a file name such as {@code 2026-09-06T14-32-05Z.json}
     * @throws NullPointerException if {@code at} is {@code null}
     */
    public static String timestampedName(Instant at) {
        Objects.requireNonNull(at, "at");
        return at.toString().replace(':', '-').replace('.', '-') + EXTENSION;
    }

    /**
     * Returns whether a name is safe to use as a backup file name.
     *
     * <p>The name comes from a command argument and is joined onto the backups folder, so anything
     * that could escape it — a separator, a traversal, a drive letter — is refused.
     *
     * @param name the proposed name, without extension
     * @return {@code true} if it is a plain file name
     */
    public static boolean isValidName(String name) {
        return name != null
                && !name.isBlank()
                && name.length() <= 64
                && name.matches("[A-Za-z0-9_.-]+")
                && !name.contains("..");
    }
}
