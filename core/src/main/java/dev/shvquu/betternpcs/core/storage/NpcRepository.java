package dev.shvquu.betternpcs.core.storage;

import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Where NPCs are persisted.
 *
 * <p>The whole storage boundary. A repository sees nothing but {@link NpcSnapshot} values, never a
 * live NPC, which is what makes it safe to run entirely off the main thread and what keeps an SQL
 * implementation from having opinions about rendering.
 *
 * <h2>Threading</h2>
 *
 * <p>Every method returns a {@link CompletableFuture} and does its work on the implementation's own
 * executor. None of them may block the calling thread — a database round trip is measured in
 * milliseconds and a server tick is fifty, so a synchronous read during a chunk load stalls the
 * whole server.
 *
 * <p>Returned futures complete on a background thread. Callers that need to touch the Bukkit API
 * afterwards must schedule that back onto the main thread themselves.
 *
 * <h2>Failure</h2>
 *
 * <p>A future completes exceptionally when the operation could not be carried out after the
 * implementation's own retries. Implementations are expected to retry transient failures — a dropped
 * connection, a deadlock — rather than surfacing them, and to make the exception message name the
 * operation without ever including credentials.
 *
 * @since 1.0.0
 */
public interface NpcRepository {

    /**
     * Opens the backend and brings its schema up to date.
     *
     * <p>Called once during plugin enable, before anything is read. Schema migrations run here, so
     * this is the one call whose failure should prevent the plugin from starting: continuing with a
     * half-migrated database risks writing data the next version cannot read.
     *
     * @return a future completing when the backend is ready
     */
    CompletableFuture<Void> initialize();

    /**
     * Loads one NPC.
     *
     * @param uniqueId the NPC's unique id
     * @return a future completing with the snapshot, or with an empty value if no NPC has that id
     * @throws NullPointerException if {@code uniqueId} is {@code null}
     */
    CompletableFuture<Optional<NpcSnapshot>> load(UUID uniqueId);

    /**
     * Loads every NPC.
     *
     * <p>Called once at startup and again on reload. Implementations should fetch in one round trip
     * rather than one per NPC.
     *
     * @return a future completing with every stored snapshot, in no particular order
     */
    CompletableFuture<List<NpcSnapshot>> loadAll();

    /**
     * Writes one NPC, inserting or updating as needed.
     *
     * @param snapshot the state to write
     * @return a future completing when the write has finished
     * @throws NullPointerException if {@code snapshot} is {@code null}
     */
    CompletableFuture<Void> save(NpcSnapshot snapshot);

    /**
     * Writes several NPCs.
     *
     * <p>Exists separately from {@link #save(NpcSnapshot)} because the periodic save cycle writes
     * every changed NPC at once, and doing that as one batch rather than as N round trips is the
     * difference between a save that finishes between ticks and one that does not.
     *
     * <p>Either every snapshot is written or none is, where the backend can express that.
     *
     * @param snapshots the states to write
     * @return a future completing when every write has finished
     * @throws NullPointerException if {@code snapshots} or any element is {@code null}
     */
    CompletableFuture<Void> saveAll(Collection<NpcSnapshot> snapshots);

    /**
     * Deletes one NPC.
     *
     * @param uniqueId the NPC's unique id
     * @return a future completing with {@code true} if a row was removed, {@code false} if there was
     *         nothing stored under that id
     * @throws NullPointerException if {@code uniqueId} is {@code null}
     */
    CompletableFuture<Boolean> delete(UUID uniqueId);

    /**
     * Closes the backend and releases its connections and threads.
     *
     * <p>Called during plugin disable, after the final save. Must tolerate being called when
     * {@link #initialize()} failed or never ran.
     *
     * @return a future completing when everything has been released
     */
    CompletableFuture<Void> shutdown();

    /**
     * Returns a short description of this backend for the startup banner.
     *
     * <p>Must never contain credentials: it is written to the console verbatim.
     *
     * @return a description such as {@code "SQLITE -> npcs.db"}
     */
    String describe();
}
