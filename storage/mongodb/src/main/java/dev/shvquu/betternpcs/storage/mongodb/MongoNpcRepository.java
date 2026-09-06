package dev.shvquu.betternpcs.storage.mongodb;

import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.core.storage.NpcRepository;
import dev.shvquu.betternpcs.core.storage.SnapshotCodec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bson.Document;

/**
 * An {@link NpcRepository} backed by MongoDB.
 *
 * <h2>The same document as the SQL backends</h2>
 *
 * <p>Identity and position are top-level fields, exactly the columns the SQL schema has, and
 * everything else is the JSON that {@link SnapshotCodec} already produces, parsed into a
 * sub-document. That is deliberate: the two backends store the same shape, so a database can be
 * inspected the same way whichever is in use, and the backup format is backend-independent.
 *
 * <p>Storing the codec output as a sub-document rather than as a string means it stays queryable
 * and readable in a Mongo shell, which is the one advantage a document store has here.
 *
 * <h2>Threading</h2>
 *
 * <p>The synchronous driver is used on this repository's own named thread pool, never on the main
 * thread. The asynchronous driver would remove a thread pool at the cost of a second programming
 * model in a codebase that already has one; the pool is the simpler choice and matches
 * {@code SqlNpcRepository}.
 *
 * @since 1.0.0
 */
public final class MongoNpcRepository implements NpcRepository {

    /** The collection NPCs live in. */
    public static final String COLLECTION = "npcs";

    /** The index that reproduces the SQL schema's unique, case-insensitive name constraint. */
    private static final String NAME_INDEX = "betternpcs_npcs_name";

    private final MongoClient client;
    private final MongoCollection<Document> npcs;
    private final ExecutorService executor;
    private final Logger logger;
    private final String description;

    private volatile boolean closed;

    /**
     * Creates the repository.
     *
     * @param client      the driver client, owned by this repository and closed with it
     * @param database    the database name
     * @param description a description safe to log, with no credentials in it
     * @param logger      where failures are reported
     * @param poolSize    how many threads to run database work on
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code poolSize} is not positive
     */
    public MongoNpcRepository(
            MongoClient client, String database, String description, Logger logger, int poolSize) {

        this.client = Objects.requireNonNull(client, "client");
        this.description = Objects.requireNonNull(description, "description");
        this.logger = Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(database, "database");

        if (poolSize < 1) {
            throw new IllegalArgumentException("The pool size must be at least 1, was " + poolSize);
        }

        MongoDatabase mongoDatabase = client.getDatabase(database);
        this.npcs = mongoDatabase.getCollection(COLLECTION);
        this.executor = Executors.newFixedThreadPool(poolSize, namedThreads());
    }

    private static ThreadFactory namedThreads() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "BetterNPCs-Storage-" + counter.incrementAndGet());
            // Daemon so that a hung query cannot keep the JVM alive after the server has stopped.
            thread.setDaemon(true);
            return thread;
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Operations
    // ---------------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<Void> initialize() {
        return run("initialize the database", () -> {
            // MongoDB has no schema to migrate, but it does need this index. Without it two NPCs
            // could share a name in the database, which the engine would then refuse to load — the
            // SQL backends enforce the same rule, and the two must not disagree.
            npcs.createIndex(
                    Indexes.ascending("name"),
                    new IndexOptions()
                            .name(NAME_INDEX)
                            .unique(true)
                            // Strength 2 compares case-insensitively but still distinguishes
                            // accents, matching how the engine looks NPCs up by name.
                            .collation(Collation.builder()
                                    .locale("en")
                                    .collationStrength(CollationStrength.SECONDARY)
                                    .build()));
            npcs.createIndex(Indexes.ascending("world"));
            return null;
        });
    }

    @Override
    public CompletableFuture<Optional<NpcSnapshot>> load(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return run("load the NPC " + uniqueId, () -> {
            Document found = npcs.find(Filters.eq("_id", uniqueId.toString())).first();
            return found == null ? Optional.<NpcSnapshot>empty() : Optional.of(read(found));
        });
    }

    @Override
    public CompletableFuture<List<NpcSnapshot>> loadAll() {
        return run("load every NPC", () -> {
            List<NpcSnapshot> snapshots = new ArrayList<>();
            for (Document document : npcs.find()) {
                String name = document.getString("name");
                try {
                    snapshots.add(read(document));
                } catch (RuntimeException unreadable) {
                    // One corrupt document must not cost the server every other NPC.
                    logger.log(Level.WARNING, unreadable,
                            () -> "Skipping the stored NPC '" + name + "': its data could not be read.");
                }
            }
            return List.copyOf(snapshots);
        });
    }

    @Override
    public CompletableFuture<Void> save(NpcSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return saveAll(List.of(snapshot));
    }

    @Override
    public CompletableFuture<Void> saveAll(Collection<NpcSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");

        List<NpcSnapshot> batch = new ArrayList<>(snapshots.size());
        for (NpcSnapshot snapshot : snapshots) {
            batch.add(Objects.requireNonNull(snapshot, "snapshot"));
        }
        if (batch.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return run("save " + batch.size() + " NPC(s)", () -> {
            List<ReplaceOneModel<Document>> writes = new ArrayList<>(batch.size());
            for (NpcSnapshot snapshot : batch) {
                writes.add(new ReplaceOneModel<>(
                        Filters.eq("_id", snapshot.uniqueId().toString()),
                        write(snapshot),
                        new ReplaceOptions().upsert(true)));
            }
            // Ordered, so that a write which violates the unique name index stops the batch rather
            // than letting later writes through — the SQL backends roll the whole batch back, and a
            // partial write would leave the database describing a state the server was never in.
            //
            // A single-node MongoDB has no multi-document transaction, so this is as close as the
            // backend allows; the difference is documented in docs/storage.md.
            npcs.bulkWrite(writes);
            return null;
        });
    }

    @Override
    public CompletableFuture<Boolean> delete(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return run("delete the NPC " + uniqueId, () ->
                npcs.deleteOne(Filters.eq("_id", uniqueId.toString())).getDeletedCount() > 0);
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }
        closed = true;

        return CompletableFuture.runAsync(() -> {
            executor.shutdown();
            try {
                // Waited on rather than killed: work queued during the final save is exactly what
                // must not be lost, and it is already in flight by the time this runs.
                if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                    logger.warning("Database work did not finish within 15 seconds; giving up on it.");
                    executor.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
            client.close();
        });
    }

    @Override
    public String describe() {
        return description;
    }

    // ---------------------------------------------------------------------------------------------
    // Document mapping
    // ---------------------------------------------------------------------------------------------

    private static Document write(NpcSnapshot snapshot) {
        NpcPosition position = snapshot.position();

        return new Document("_id", snapshot.uniqueId().toString())
                .append("name", snapshot.name())
                .append("type", snapshot.type().id())
                .append("world", position.world())
                .append("x", position.x())
                .append("y", position.y())
                .append("z", position.z())
                .append("yaw", (double) position.yaw())
                .append("pitch", (double) position.pitch())
                .append("spawnByDefault", snapshot.spawnByDefault())
                // Parsed rather than stored as a string, so the document stays readable and
                // queryable in a Mongo shell.
                .append("data", Document.parse(SnapshotCodec.encode(snapshot)));
    }

    private static NpcSnapshot read(Document document) {
        NpcPosition position = new NpcPosition(
                document.getString("world"),
                document.getDouble("x"),
                document.getDouble("y"),
                document.getDouble("z"),
                document.getDouble("yaw").floatValue(),
                document.getDouble("pitch").floatValue());

        Document data = document.get("data", Document.class);

        return SnapshotCodec.decode(
                UUID.fromString(document.getString("_id")),
                document.getString("name"),
                document.getString("type"),
                position,
                document.getBoolean("spawnByDefault", true),
                data == null ? null : data.toJson());
    }

    // ---------------------------------------------------------------------------------------------
    // Execution
    // ---------------------------------------------------------------------------------------------

    /**
     * Work that talks to the database.
     *
     * @param <T> what it produces
     */
    @FunctionalInterface
    private interface DatabaseWork<T> {

        /**
         * Runs the work.
         *
         * @return the result
         * @throws MongoException if the database rejected it
         */
        T run() throws MongoException;
    }

    private <T> CompletableFuture<T> run(String what, DatabaseWork<T> work) {
        if (closed) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("The repository has been shut down"));
        }

        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    result.complete(work.run());
                } catch (Throwable failure) {
                    // The message names the operation, never the connection string: this reaches
                    // the console and, from there, public issue trackers.
                    result.completeExceptionally(
                            new MongoException("Could not " + what, failure));
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
            result.completeExceptionally(
                    new IllegalStateException("The repository is shutting down", shuttingDown));
        }
        return result;
    }

    /**
     * Returns whether a failure was a duplicate key, which for this collection always means a name
     * that is already taken.
     *
     * @param failure the failure to classify
     * @return {@code true} if the unique name index rejected the write
     */
    public static boolean isDuplicateName(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof MongoWriteException write) {
                return write.getError().getCategory()
                        == com.mongodb.ErrorCategory.DUPLICATE_KEY;
            }
            if (current instanceof com.mongodb.MongoBulkWriteException bulk) {
                return bulk.getWriteErrors().stream().anyMatch(error -> error.getCode() == 11000);
            }
        }
        return false;
    }
}
