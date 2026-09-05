package dev.shvquu.betternpcs.storage.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.shvquu.betternpcs.core.config.StorageSettings;
import dev.shvquu.betternpcs.core.config.StorageType;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Builds a {@link SqlNpcRepository} from the configured settings.
 *
 * <p>All the connection-pool tuning lives here rather than in the repository, because it is entirely
 * about the specific database and not about NPCs.
 *
 * @since 1.0.0
 */
public final class SqlRepositoryFactory {

    /**
     * How many connections SQLite is given.
     *
     * <p>One, on purpose. SQLite serialises writes at the file level, so extra connections do not
     * make writes faster — they make them contend, and the resulting {@code SQLITE_BUSY} failures
     * look like random save errors. One connection turns contention into a queue.
     */
    private static final int SQLITE_POOL_SIZE = 1;

    private SqlRepositoryFactory() {
        throw new AssertionError("No instances");
    }

    /**
     * Creates a repository for the configured backend.
     *
     * @param settings   the storage section of the configuration
     * @param dataFolder the plugin's data folder, where a SQLite file is placed
     * @param logger     where the repository reports failures
     * @return the repository, not yet initialised
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the configured type is not served over JDBC
     */
    public static SqlNpcRepository create(
            StorageSettings settings, Path dataFolder, Logger logger) {

        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(logger, "logger");

        StorageType type = settings.type();
        SqlDialect dialect = SqlDialect.forType(type);

        HikariConfig config = new HikariConfig();
        config.setPoolName("BetterNPCs-" + type.name());
        // Failing fast at startup beats a server that appears to boot and then cannot save. The
        // plugin catches this and falls back to in-memory storage with a loud warning.
        config.setInitializationFailTimeout(10_000L);

        int poolSize;
        if (type == StorageType.SQLITE) {
            Path file = dataFolder.resolve(settings.sqlite().fileName());
            config.setJdbcUrl("jdbc:sqlite:" + file.toAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            poolSize = SQLITE_POOL_SIZE;

            // Write-ahead logging lets reads proceed while a write is in progress, which matters
            // because the save cycle and a /npc info can easily overlap.
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("synchronous", "NORMAL");
            config.addDataSourceProperty("foreign_keys", "true");
        } else {
            StorageSettings.Jdbc jdbc = settings.activeJdbc();
            config.setJdbcUrl(jdbcUrl(type, jdbc));
            config.setUsername(jdbc.username());
            config.setPassword(jdbc.password());
            config.setConnectionTimeout(jdbc.connectionTimeout());
            poolSize = jdbc.poolSize();

            jdbc.properties().forEach(config::addDataSourceProperty);

            // Shorter than the eight-hour idle timeout MySQL defaults to, so the pool retires a
            // connection before the server silently drops it.
            config.setMaxLifetime(600_000L);
            config.setKeepaliveTime(300_000L);
        }

        config.setMaximumPoolSize(poolSize);

        return new SqlNpcRepository(
                new HikariDataSource(config), dialect, describe(settings), logger, poolSize);
    }

    private static String jdbcUrl(StorageType type, StorageSettings.Jdbc jdbc) {
        return switch (type) {
            case MYSQL -> "jdbc:mysql://" + jdbc.host() + ":" + jdbc.port() + "/" + jdbc.database();
            case MARIADB -> "jdbc:mariadb://" + jdbc.host() + ":" + jdbc.port() + "/" + jdbc.database();
            case POSTGRESQL ->
                    "jdbc:postgresql://" + jdbc.host() + ":" + jdbc.port() + "/" + jdbc.database();
            case SQLITE, MONGODB -> throw new IllegalArgumentException(
                    type + " does not use a network JDBC URL");
        };
    }

    /**
     * Returns a description of the configured backend with no credentials in it.
     *
     * @param settings the storage settings
     * @return the description, safe to log
     */
    public static String describe(StorageSettings settings) {
        return switch (settings.type()) {
            case SQLITE -> "SQLITE -> " + settings.sqlite().fileName();
            case MYSQL, MARIADB, POSTGRESQL ->
                    settings.type() + " -> " + settings.activeJdbc().describeTarget();
            case MONGODB -> "MONGODB -> " + settings.mongodb().describeTarget();
        };
    }
}
