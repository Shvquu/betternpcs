package dev.shvquu.betternpcs.core.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * How and where NPCs are persisted.
 *
 * <p>Every block of the {@code storage} section is parsed, not only the one the configured
 * {@link #type()} names. Parsing all of them means a typo in the MySQL block is reported at startup
 * even while SQLite is in use, rather than the first time someone switches over.
 *
 * <h2>Secrets</h2>
 *
 * <p>Passwords and connection strings live in here, so every {@code toString} in this file redacts
 * them. That matters more than it looks: settings objects end up in debug logs, in exception
 * messages and in crash reports that get pasted into public issue trackers, and a MongoDB
 * connection string carries its credentials inline.
 *
 * @param type       which backend to use
 * @param sqlite     the SQLite block
 * @param mysql      the MySQL block, also used for MariaDB
 * @param postgresql the PostgreSQL block
 * @param mongodb    the MongoDB block
 * @since 1.0.0
 */
public record StorageSettings(
        StorageType type,
        Sqlite sqlite,
        Jdbc mysql,
        Jdbc postgresql,
        Mongo mongodb) {

    /** What is printed instead of a secret. */
    public static final String REDACTED = "<redacted>";

    /**
     * Creates the settings.
     *
     * @param type       which backend to use
     * @param sqlite     the SQLite block
     * @param mysql      the MySQL block
     * @param postgresql the PostgreSQL block
     * @param mongodb    the MongoDB block
     * @throws NullPointerException if any argument is {@code null}
     */
    public StorageSettings {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sqlite, "sqlite");
        Objects.requireNonNull(mysql, "mysql");
        Objects.requireNonNull(postgresql, "postgresql");
        Objects.requireNonNull(mongodb, "mongodb");
    }

    /**
     * Returns the JDBC block for the configured type.
     *
     * <p>MariaDB reads the MySQL block: the two share every setting, and asking an administrator to
     * duplicate the block just to change the driver would be busywork.
     *
     * @return the JDBC settings in force
     * @throws IllegalStateException if the configured type is not served over JDBC
     */
    public Jdbc activeJdbc() {
        return switch (type) {
            case MYSQL, MARIADB -> mysql;
            case POSTGRESQL -> postgresql;
            case SQLITE, MONGODB -> throw new IllegalStateException(
                    "Storage type " + type + " is not a JDBC network backend");
        };
    }

    /**
     * Returns a description safe to log, naming only the active backend.
     *
     * @return the description
     */
    @Override
    public String toString() {
        String detail = switch (type) {
            case SQLITE -> sqlite.fileName();
            case MYSQL, MARIADB -> mysql.describeTarget();
            case POSTGRESQL -> postgresql.describeTarget();
            case MONGODB -> mongodb.describeTarget();
        };
        return "StorageSettings[" + type + " -> " + detail + ']';
    }

    /**
     * The SQLite block.
     *
     * @param fileName the database file name, resolved against the plugin's data folder
     * @since 1.0.0
     */
    public record Sqlite(String fileName) {

        /**
         * Creates the settings.
         *
         * @param fileName the database file name
         * @throws NullPointerException     if {@code fileName} is {@code null}
         * @throws IllegalArgumentException if {@code fileName} is blank, absolute, or escapes the
         *                                  data folder
         */
        public Sqlite {
            Objects.requireNonNull(fileName, "fileName");
            fileName = fileName.trim();
            if (fileName.isEmpty()) {
                throw new IllegalArgumentException("storage.sqlite.file must not be blank");
            }
            // The value comes from a file an administrator edits, and is joined onto the data
            // folder path. Refusing traversal and absolute paths keeps a mistyped or malicious value
            // from pointing the database at somewhere else on the disk entirely.
            if (fileName.contains("..") || fileName.startsWith("/") || fileName.startsWith("\\")
                    || fileName.contains(":")) {
                throw new IllegalArgumentException(
                        "storage.sqlite.file must be a plain file name inside the plugin folder, was '"
                                + fileName + "'");
            }
        }
    }

    /**
     * A JDBC network backend block.
     *
     * @param host              the server host
     * @param port              the server port
     * @param database          the database name
     * @param username          the user to connect as
     * @param password          the password; never logged
     * @param poolSize          the maximum number of pooled connections
     * @param connectionTimeout how long to wait for a connection, in milliseconds
     * @param properties        extra JDBC properties passed to the driver
     * @since 1.0.0
     */
    public record Jdbc(
            String host,
            int port,
            String database,
            String username,
            String password,
            int poolSize,
            long connectionTimeout,
            Map<String, String> properties) {

        /**
         * Creates the settings.
         *
         * @param host              the server host
         * @param port              the server port
         * @param database          the database name
         * @param username          the user to connect as
         * @param password          the password
         * @param poolSize          the maximum number of pooled connections
         * @param connectionTimeout how long to wait for a connection, in milliseconds
         * @param properties        extra JDBC properties
         * @throws NullPointerException     if any argument except {@code password} is {@code null}
         * @throws IllegalArgumentException if the host or database is blank, the port is outside
         *                                  1..65535, the pool size is not positive, or the timeout is
         *                                  below 250 ms
         */
        public Jdbc {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(database, "database");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(properties, "properties");
            host = host.trim();
            database = database.trim();
            username = username.trim();
            password = password == null ? "" : password;
            if (host.isEmpty()) {
                throw new IllegalArgumentException("Database host must not be blank");
            }
            if (database.isEmpty()) {
                throw new IllegalArgumentException("Database name must not be blank");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Database port must be 1..65535, was " + port);
            }
            if (poolSize < 1) {
                throw new IllegalArgumentException("Pool size must be at least 1, was " + poolSize);
            }
            if (connectionTimeout < 250) {
                // HikariCP itself refuses anything below 250 ms; catching it here names the setting.
                throw new IllegalArgumentException(
                        "Connection timeout must be at least 250 ms, was " + connectionTimeout);
            }
            properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        }

        /**
         * Returns {@code host:port/database}, with no credentials.
         *
         * @return a description safe to log
         */
        public String describeTarget() {
            return host + ":" + port + "/" + database;
        }

        /**
         * Returns whether a password was configured.
         *
         * <p>Lets a startup check warn about an empty password without printing it.
         *
         * @return {@code true} if the password is not empty
         */
        public boolean hasPassword() {
            return !password.isEmpty();
        }

        /**
         * Returns a description with the password redacted.
         *
         * @return the description
         */
        @Override
        public String toString() {
            return "Jdbc[" + describeTarget() + ", user=" + username + ", password=" + REDACTED
                    + ", pool=" + poolSize + ']';
        }
    }

    /**
     * The MongoDB block.
     *
     * @param connectionString the connection string; may contain credentials and is never logged
     * @param database         the database name
     * @since 1.0.0
     */
    public record Mongo(String connectionString, String database) {

        /**
         * Creates the settings.
         *
         * @param connectionString the connection string
         * @param database         the database name
         * @throws NullPointerException     if either argument is {@code null}
         * @throws IllegalArgumentException if either is blank, or the connection string is not a
         *                                  {@code mongodb://} or {@code mongodb+srv://} URI
         */
        public Mongo {
            Objects.requireNonNull(connectionString, "connectionString");
            Objects.requireNonNull(database, "database");
            connectionString = connectionString.trim();
            database = database.trim();
            if (connectionString.isEmpty()) {
                throw new IllegalArgumentException("storage.mongodb.connection-string must not be blank");
            }
            if (database.isEmpty()) {
                throw new IllegalArgumentException("storage.mongodb.database must not be blank");
            }
            if (!connectionString.startsWith("mongodb://") && !connectionString.startsWith("mongodb+srv://")) {
                // Checked here so that the failure names the setting, rather than surfacing as a
                // driver exception whose message would repeat the credential-bearing string.
                throw new IllegalArgumentException(
                        "storage.mongodb.connection-string must start with mongodb:// or mongodb+srv://");
            }
        }

        /**
         * Returns the host part of the connection string, with any credentials removed.
         *
         * @return a description safe to log
         */
        public String describeTarget() {
            int schemeEnd = connectionString.indexOf("://");
            String remainder = schemeEnd < 0 ? connectionString : connectionString.substring(schemeEnd + 3);
            // Everything before '@' is "user:password". Dropping it is the whole point.
            int credentialsEnd = remainder.indexOf('@');
            if (credentialsEnd >= 0) {
                remainder = REDACTED + "@" + remainder.substring(credentialsEnd + 1);
            }
            int pathStart = remainder.indexOf('/');
            if (pathStart >= 0) {
                remainder = remainder.substring(0, pathStart);
            }
            int queryStart = remainder.indexOf('?');
            if (queryStart >= 0) {
                remainder = remainder.substring(0, queryStart);
            }
            return remainder + "/" + database;
        }

        /**
         * Returns a description with any credentials redacted.
         *
         * @return the description
         */
        @Override
        public String toString() {
            return "Mongo[" + describeTarget() + ']';
        }
    }
}
