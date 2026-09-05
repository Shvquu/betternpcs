package dev.shvquu.betternpcs.storage.sql;

import com.zaxxer.hikari.HikariDataSource;
import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.core.storage.NpcRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientException;
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
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An {@link NpcRepository} backed by a JDBC database.
 *
 * <h2>Threading</h2>
 *
 * <p>Every operation runs on this repository's own executor, never on the main thread and never on
 * the common fork-join pool. A dedicated pool matters for two reasons: a database call that blocks
 * would starve other work sharing the common pool, and a named thread makes it obvious in a thread
 * dump which plugin is waiting on a database.
 *
 * <h2>Retries</h2>
 *
 * <p>Transient failures — a connection dropped by a firewall's idle timeout, a deadlock, a database
 * restarting — are retried with a growing delay before the future is failed. Without that, a
 * momentary network blip during a save cycle would surface as lost NPC changes. Failures that
 * retrying cannot fix, such as a syntax error or a constraint violation, are not retried: doing so
 * would turn one clear error into several identical ones a few seconds apart.
 *
 * @since 1.0.0
 */
public final class SqlNpcRepository implements NpcRepository {

    /** How many times a transient failure is retried before the operation is given up on. */
    private static final int MAX_ATTEMPTS = 3;

    /** How long to wait before the first retry; doubled for each subsequent one. */
    private static final long FIRST_RETRY_DELAY_MILLIS = 200L;

    private static final String SELECT_COLUMNS =
            "uuid, name, type, world, x, y, z, yaw, pitch, spawn_by_default, data";

    private final HikariDataSource dataSource;
    private final SqlDialect dialect;
    private final ExecutorService executor;
    private final Logger logger;
    private final String description;

    private volatile boolean closed;

    /**
     * Creates the repository.
     *
     * @param dataSource  the connection pool, owned by this repository and closed with it
     * @param dialect     the dialect matching the pool's database
     * @param description a description safe to log, with no credentials in it
     * @param logger      where failures are reported
     * @param poolSize    how many threads to run database work on
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code poolSize} is not positive
     */
    public SqlNpcRepository(
            HikariDataSource dataSource,
            SqlDialect dialect,
            String description,
            Logger logger,
            int poolSize) {

        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.description = Objects.requireNonNull(description, "description");
        this.logger = Objects.requireNonNull(logger, "logger");
        if (poolSize < 1) {
            throw new IllegalArgumentException("The pool size must be at least 1, was " + poolSize);
        }
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
            try (Connection connection = dataSource.getConnection()) {
                new SchemaMigrations(dialect, logger).apply(connection);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Optional<NpcSnapshot>> load(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return run("load the NPC " + uniqueId, () -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT " + SELECT_COLUMNS + " FROM " + SqlDialect.NPC_TABLE
                                    + " WHERE uuid = ?")) {
                query.setString(1, uniqueId.toString());
                try (ResultSet rows = query.executeQuery()) {
                    return rows.next() ? Optional.of(read(rows)) : Optional.<NpcSnapshot>empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<NpcSnapshot>> loadAll() {
        return run("load every NPC", () -> {
            List<NpcSnapshot> snapshots = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT " + SELECT_COLUMNS + " FROM " + SqlDialect.NPC_TABLE);
                    ResultSet rows = query.executeQuery()) {

                while (rows.next()) {
                    String name = rows.getString("name");
                    try {
                        snapshots.add(read(rows));
                    } catch (RuntimeException unreadable) {
                        // One corrupt row must not cost the server every other NPC. Reported in
                        // full, because it is a row somebody will have to look at.
                        logger.log(Level.WARNING, unreadable,
                                () -> "Skipping the stored NPC '" + name + "': its data could not be read.");
                    }
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
            try (Connection connection = dataSource.getConnection()) {
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (PreparedStatement upsert = connection.prepareStatement(dialect.upsertNpc())) {
                    for (NpcSnapshot snapshot : batch) {
                        bind(upsert, snapshot);
                        upsert.addBatch();
                    }
                    upsert.executeBatch();
                    // One transaction for the whole batch: the periodic save cycle writes every
                    // changed NPC at once, and a partial write would leave the database describing a
                    // state the server was never in.
                    connection.commit();
                } catch (SQLException failure) {
                    connection.rollback();
                    throw failure;
                } finally {
                    connection.setAutoCommit(autoCommit);
                }
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Boolean> delete(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return run("delete the NPC " + uniqueId, () -> {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM " + SqlDialect.NPC_TABLE + " WHERE uuid = ?")) {
                statement.setString(1, uniqueId.toString());
                return statement.executeUpdate() > 0;
            }
        });
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
            dataSource.close();
        });
    }

    @Override
    public String describe() {
        return description;
    }

    // ---------------------------------------------------------------------------------------------
    // Row mapping
    // ---------------------------------------------------------------------------------------------

    private void bind(PreparedStatement statement, NpcSnapshot snapshot) throws SQLException {
        NpcPosition position = snapshot.position();

        statement.setString(1, snapshot.uniqueId().toString());
        statement.setString(2, snapshot.name());
        statement.setString(3, snapshot.type().id());
        statement.setString(4, position.world());
        statement.setDouble(5, position.x());
        statement.setDouble(6, position.y());
        statement.setDouble(7, position.z());
        statement.setFloat(8, position.yaw());
        statement.setFloat(9, position.pitch());

        if (dialect.hasNativeBooleans()) {
            statement.setBoolean(10, snapshot.spawnByDefault());
        } else {
            statement.setInt(10, snapshot.spawnByDefault() ? 1 : 0);
        }

        statement.setString(11, SnapshotCodec.encode(snapshot));
    }

    private NpcSnapshot read(ResultSet rows) throws SQLException {
        NpcPosition position = new NpcPosition(
                rows.getString("world"),
                rows.getDouble("x"),
                rows.getDouble("y"),
                rows.getDouble("z"),
                rows.getFloat("yaw"),
                rows.getFloat("pitch"));

        boolean spawnByDefault = dialect.hasNativeBooleans()
                ? rows.getBoolean("spawn_by_default")
                : rows.getInt("spawn_by_default") != 0;

        return SnapshotCodec.decode(
                UUID.fromString(rows.getString("uuid")),
                rows.getString("name"),
                rows.getString("type"),
                position,
                spawnByDefault,
                rows.getString("data"));
    }

    // ---------------------------------------------------------------------------------------------
    // Execution and retries
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
         * @throws SQLException if the database rejected it
         */
        T run() throws SQLException;
    }

    private <T> CompletableFuture<T> run(String description, DatabaseWork<T> work) {
        if (closed) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("The repository has been shut down"));
        }

        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    result.complete(withRetries(description, work));
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
            result.completeExceptionally(
                    new IllegalStateException("The repository is shutting down", shuttingDown));
        }
        return result;
    }

    private <T> T withRetries(String description, DatabaseWork<T> work) throws SQLException {
        SQLException lastFailure = null;
        long delay = FIRST_RETRY_DELAY_MILLIS;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return work.run();
            } catch (SQLException failure) {
                lastFailure = failure;
                if (!isTransient(failure) || attempt == MAX_ATTEMPTS) {
                    break;
                }

                int nextAttempt = attempt + 1;
                long waited = delay;
                logger.log(Level.WARNING, () ->
                        "Could not " + description + " (attempt " + (nextAttempt - 1) + " of "
                                + MAX_ATTEMPTS + "): " + failure.getMessage()
                                + ". Retrying in " + waited + " ms.");
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
                delay *= 2;
            }
        }

        // The message names the operation, never the connection details: this reaches the console
        // and, from there, public issue trackers.
        throw new SQLException("Could not " + description + " after " + MAX_ATTEMPTS
                + " attempt(s). See the cause for details.", lastFailure);
    }

    /**
     * Returns whether a failure is worth retrying.
     *
     * <p>A dropped connection or a deadlock will very likely succeed a moment later. A syntax error
     * or a unique constraint violation will not, and retrying it only produces the same error
     * several times with delays in between, burying the first one.
     *
     * @param failure the failure to classify
     * @return {@code true} if the operation should be retried
     */
    static boolean isTransient(SQLException failure) {
        if (failure instanceof SQLTransientException) {
            return true;
        }
        for (SQLException current = failure; current != null; current = current.getNextException()) {
            String state = current.getSQLState();
            if (state == null) {
                continue;
            }
            // 08xxx is "connection exception" and 40xxx is "transaction rollback", which together
            // cover the dropped connections and deadlocks worth trying again.
            if (state.startsWith("08") || state.startsWith("40")) {
                return true;
            }
        }
        return failure.getCause() instanceof java.io.IOException;
    }

    /**
     * Returns the connection pool, for tests that need to inspect it.
     *
     * @return the data source
     */
    HikariDataSource dataSource() {
        return dataSource;
    }

    /**
     * Runs work on the repository's executor, for callers that need to chain onto it.
     *
     * @param supplier what to run
     * @param <T>      the result type
     * @return a future completing with the result
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }
}
