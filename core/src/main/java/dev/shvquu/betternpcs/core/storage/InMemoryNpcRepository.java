package dev.shvquu.betternpcs.core.storage;

import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An {@link NpcRepository} that keeps everything in memory and loses it on shutdown.
 *
 * <p>Two real uses. It is what the engine's tests run against, so the engine can be exercised
 * without a database or a server. And it is the fallback the plugin switches to when the configured
 * backend cannot be reached at startup: NPCs then work for the session and simply are not persisted,
 * which is a far better outcome for a server owner than the plugin refusing to load.
 *
 * <p>Thread safe. Futures complete on the calling thread, which is deliberate — a test that had to
 * wait for another thread would be slower and flakier for no benefit, and the fallback case has no
 * work worth moving.
 *
 * @since 1.0.0
 */
public final class InMemoryNpcRepository implements NpcRepository {

    private final Map<UUID, NpcSnapshot> snapshots = new ConcurrentHashMap<>();
    private final AtomicInteger saveCount = new AtomicInteger();
    private final AtomicInteger loadCount = new AtomicInteger();
    private volatile boolean closed;

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Optional<NpcSnapshot>> load(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return completed(Optional.ofNullable(snapshots.get(uniqueId)));
    }

    @Override
    public CompletableFuture<List<NpcSnapshot>> loadAll() {
        loadCount.incrementAndGet();
        return completed(List.copyOf(snapshots.values()));
    }

    @Override
    public CompletableFuture<Void> save(NpcSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        saveCount.incrementAndGet();
        snapshots.put(snapshot.uniqueId(), snapshot);
        return completed(null);
    }

    @Override
    public CompletableFuture<Void> saveAll(Collection<NpcSnapshot> toSave) {
        Objects.requireNonNull(toSave, "snapshots");
        // Copied before anything is written so that a null element fails the whole batch, matching
        // the all-or-nothing guarantee a transactional backend gives.
        List<NpcSnapshot> batch = new ArrayList<>(toSave.size());
        for (NpcSnapshot snapshot : toSave) {
            batch.add(Objects.requireNonNull(snapshot, "snapshot"));
        }
        saveCount.incrementAndGet();
        batch.forEach(snapshot -> snapshots.put(snapshot.uniqueId(), snapshot));
        return completed(null);
    }

    @Override
    public CompletableFuture<Boolean> delete(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return completed(snapshots.remove(uniqueId) != null);
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        closed = true;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String describe() {
        return "MEMORY (not persisted)";
    }

    private <T> CompletableFuture<T> completed(T value) {
        if (closed) {
            // Silently succeeding after shutdown would hide a lifecycle bug — a save scheduled
            // during disable that the engine failed to wait for.
            return CompletableFuture.failedFuture(
                    new IllegalStateException("The repository has been shut down"));
        }
        return CompletableFuture.completedFuture(value);
    }

    /**
     * Returns how many write operations have been performed.
     *
     * <p>For tests that assert the engine does not write when nothing changed.
     *
     * @return the number of {@link #save} and {@link #saveAll} calls
     */
    public int saveCount() {
        return saveCount.get();
    }

    /**
     * Returns how many times every NPC has been loaded.
     *
     * @return the number of {@link #loadAll()} calls
     */
    public int loadCount() {
        return loadCount.get();
    }

    /**
     * Returns how many NPCs are stored.
     *
     * @return the number of stored snapshots
     */
    public int size() {
        return snapshots.size();
    }

    /**
     * Stores a snapshot directly, bypassing the future.
     *
     * <p>For tests that need existing data before the engine starts.
     *
     * @param snapshot the snapshot to store
     * @throws NullPointerException if {@code snapshot} is {@code null}
     */
    public void put(NpcSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        snapshots.put(snapshot.uniqueId(), snapshot);
    }
}
