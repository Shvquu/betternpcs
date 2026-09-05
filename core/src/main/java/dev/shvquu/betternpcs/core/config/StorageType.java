package dev.shvquu.betternpcs.core.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The storage backends BetterNPCs can persist NPCs to.
 *
 * @since 1.0.0
 */
public enum StorageType {

    /**
     * A file-backed SQL database. The default, and the right choice for a single server.
     *
     * <p>Needs no setup, keeps everything in one file next to the plugin, and handles thousands of
     * NPCs without difficulty. Its one real limitation is that it cannot be shared between servers.
     */
    SQLITE(Family.SQL),

    /** MySQL. Use this when several servers share one set of NPCs. */
    MYSQL(Family.SQL),

    /**
     * MariaDB.
     *
     * <p>A separate constant rather than an alias of {@link #MYSQL}: the two need different JDBC
     * drivers, and reporting the one the administrator actually configured makes a connection
     * failure far easier to diagnose.
     */
    MARIADB(Family.SQL),

    /** PostgreSQL. */
    POSTGRESQL(Family.SQL),

    /** MongoDB. */
    MONGODB(Family.DOCUMENT);

    /**
     * The broad kind of a storage backend, which decides which implementation module serves it.
     *
     * @since 1.0.0
     */
    public enum Family {
        /** Served by a JDBC connection pool and a migrating schema. */
        SQL,
        /** Served by a document store with no schema of its own. */
        DOCUMENT
    }

    private final Family family;

    StorageType(Family family) {
        this.family = family;
    }

    /**
     * Returns which kind of backend this is.
     *
     * @return the family
     */
    public Family family() {
        return family;
    }

    /**
     * Returns whether this backend is served over JDBC.
     *
     * @return {@code true} for every SQL backend
     */
    public boolean isSql() {
        return family == Family.SQL;
    }

    /**
     * Parses a configured storage type, case insensitively.
     *
     * @param value the configured value
     * @return the storage type, or empty if the value names none
     */
    public static Optional<StorageType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(values()).filter(type -> type.name().equals(normalised)).findFirst();
    }

    /**
     * Returns the accepted values, for an error message that tells the administrator what to write.
     *
     * @return a comma-separated list
     */
    public static String supportedValues() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
