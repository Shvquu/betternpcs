package dev.shvquu.betternpcs.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Brings a database's schema up to date, recording what it has already done.
 *
 * <p>Migrations are numbered and run in order, each in its own transaction, and each is recorded in
 * {@link SqlDialect#SCHEMA_TABLE} only once its statements have committed. A migration that fails
 * therefore leaves the database at the previous version rather than half-way through a new one,
 * which is the difference between "start the old version again" and "restore a backup".
 *
 * <p>Migrations are never edited once released. Changing one would leave every existing database
 * with the old shape and every new one with the new shape, and nothing to tell them apart. A change
 * is a new migration.
 *
 * @since 1.0.0
 */
public final class SchemaMigrations {

    /**
     * One numbered change to the schema.
     *
     * @param version    the version this migration produces, counting from one
     * @param name       a short description, recorded in the log
     * @param statements the statements to run, in order
     * @since 1.0.0
     */
    public record Migration(int version, String name, List<String> statements) {

        /**
         * Creates a migration.
         *
         * @param version    the version it produces
         * @param name       a short description
         * @param statements the statements to run
         * @throws NullPointerException     if {@code name} or {@code statements} is {@code null}
         * @throws IllegalArgumentException if {@code version} is below one or there are no statements
         */
        public Migration {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(statements, "statements");
            if (version < 1) {
                throw new IllegalArgumentException("A migration version starts at 1, was " + version);
            }
            if (statements.isEmpty()) {
                throw new IllegalArgumentException("Migration " + version + " has no statements");
            }
            statements = List.copyOf(statements);
        }
    }

    private final SqlDialect dialect;
    private final Logger logger;

    /**
     * Creates the migrator.
     *
     * @param dialect the dialect whose statements to run
     * @param logger  where progress is reported
     * @throws NullPointerException if either argument is {@code null}
     */
    public SchemaMigrations(SqlDialect dialect, Logger logger) {
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Returns the migrations this build knows about, in order.
     *
     * @return the migrations
     */
    public List<Migration> migrations() {
        List<Migration> migrations = new ArrayList<>();
        migrations.add(new Migration(1, "initial schema", dialect.createSchema()));
        return List.copyOf(migrations);
    }

    /**
     * Applies every migration the database has not yet seen.
     *
     * @param connection an open connection; left open
     * @return the number of migrations applied
     * @throws SQLException if a migration failed, leaving the database at the last version that
     *                      committed
     */
    public int apply(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection");

        try (Statement statement = connection.createStatement()) {
            statement.execute(dialect.createSchemaTable());
        }

        Map<Integer, String> applied = alreadyApplied(connection);
        int count = 0;

        for (Migration migration : migrations()) {
            if (applied.containsKey(migration.version())) {
                continue;
            }
            run(connection, migration);
            count++;
        }

        if (count > 0) {
            int total = count;
            logger.info(() -> "Applied " + total + " database migration(s).");
        }
        return count;
    }

    private void run(Connection connection, Migration migration) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                for (String sql : migration.statements()) {
                    statement.execute(sql);
                }
            }
            try (PreparedStatement record = connection.prepareStatement(
                    "INSERT INTO " + SqlDialect.SCHEMA_TABLE + " (version, applied_at) VALUES (?, ?)")) {
                record.setInt(1, migration.version());
                record.setString(2, Instant.now().toString());
                record.executeUpdate();
            }
            connection.commit();
            logger.info(() -> "Database migration " + migration.version()
                    + " (" + migration.name() + ") applied.");
        } catch (SQLException failure) {
            // Rolled back so the database stays at the previous version. Half a migration is far
            // worse than none: the old plugin version can still read the old shape.
            connection.rollback();
            throw new SQLException(
                    "Database migration " + migration.version() + " (" + migration.name()
                            + ") failed and was rolled back", failure);
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private Map<Integer, String> alreadyApplied(Connection connection) throws SQLException {
        Map<Integer, String> applied = new LinkedHashMap<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT version, applied_at FROM " + SqlDialect.SCHEMA_TABLE + " ORDER BY version");
                ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                applied.put(rows.getInt("version"), rows.getString("applied_at"));
            }
        }
        return applied;
    }

    /**
     * Returns the schema version this build expects.
     *
     * @return the highest known migration version
     */
    public int targetVersion() {
        return migrations().stream().mapToInt(Migration::version).max().orElse(0);
    }
}
