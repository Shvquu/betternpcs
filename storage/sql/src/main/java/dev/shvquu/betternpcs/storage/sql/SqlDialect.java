package dev.shvquu.betternpcs.storage.sql;

import dev.shvquu.betternpcs.core.config.StorageType;
import java.util.List;
import java.util.Objects;

/**
 * The handful of statements that genuinely differ between the SQL engines BetterNPCs supports.
 *
 * <p>Everything else — the reads, the delete, the schema version bookkeeping — is standard SQL and
 * lives in {@link SqlNpcRepository}. Only the parts where the engines disagree are here, which keeps
 * the differences small enough to see at a glance:
 *
 * <ul>
 *   <li>the type of a large text column</li>
 *   <li>the upsert syntax, where MySQL went its own way</li>
 *   <li>whether a boolean is a boolean</li>
 * </ul>
 *
 * @since 1.0.0
 */
public interface SqlDialect {

    /** The table holding NPCs. */
    String NPC_TABLE = "betternpcs_npcs";

    /** The table recording which schema migrations have run. */
    String SCHEMA_TABLE = "betternpcs_schema";

    /**
     * Returns the dialect for a storage type.
     *
     * @param type the configured backend
     * @return the dialect
     * @throws NullPointerException     if {@code type} is {@code null}
     * @throws IllegalArgumentException if {@code type} is not served over JDBC
     */
    static SqlDialect forType(StorageType type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case SQLITE -> new Sqlite();
            case MYSQL, MARIADB -> new MySql();
            case POSTGRESQL -> new PostgreSql();
            case MONGODB -> throw new IllegalArgumentException(
                    "MongoDB is not a SQL backend and is not served by this repository");
        };
    }

    /**
     * Returns the statements that create the schema from nothing.
     *
     * @return the DDL statements, in the order they must run
     */
    List<String> createSchema();

    /**
     * Returns the statement that inserts an NPC or updates it if its id is already present.
     *
     * <p>Parameters, in order: uuid, name, type, world, x, y, z, yaw, pitch, spawn_by_default, data.
     * The same eleven are bound whether the row is new or not, so callers do not have to know which
     * shape the dialect produced.
     *
     * @return the upsert statement
     */
    String upsertNpc();

    /**
     * Returns whether this dialect binds booleans as booleans rather than as integers.
     *
     * <p>SQLite has no boolean type; binding one through JDBC works, but reading it back as a
     * boolean does not on every driver version, so the repository reads an integer there.
     *
     * @return {@code true} if the driver has a real boolean type
     */
    boolean hasNativeBooleans();

    /**
     * Returns the statements that create the schema version table.
     *
     * @return the DDL statement
     */
    String createSchemaTable();

    /**
     * SQLite.
     *
     * @since 1.0.0
     */
    final class Sqlite implements SqlDialect {

        @Override
        public List<String> createSchema() {
            return List.of(
                    """
                    CREATE TABLE IF NOT EXISTS betternpcs_npcs (
                        uuid              TEXT    NOT NULL PRIMARY KEY,
                        name              TEXT    NOT NULL,
                        type              TEXT    NOT NULL,
                        world             TEXT    NOT NULL,
                        x                 REAL    NOT NULL,
                        y                 REAL    NOT NULL,
                        z                 REAL    NOT NULL,
                        yaw               REAL    NOT NULL,
                        pitch             REAL    NOT NULL,
                        spawn_by_default  INTEGER NOT NULL DEFAULT 1,
                        data              TEXT    NOT NULL DEFAULT '{}'
                    )
                    """,
                    // Names are the user-facing identity and must be unique. Enforced by the database
                    // rather than only in memory, so a hand-edited file cannot produce two NPCs the
                    // engine would then refuse to load.
                    "CREATE UNIQUE INDEX IF NOT EXISTS betternpcs_npcs_name ON betternpcs_npcs (name COLLATE NOCASE)",
                    "CREATE INDEX IF NOT EXISTS betternpcs_npcs_world ON betternpcs_npcs (world)");
        }

        @Override
        public String createSchemaTable() {
            return """
                   CREATE TABLE IF NOT EXISTS betternpcs_schema (
                       version     INTEGER NOT NULL PRIMARY KEY,
                       applied_at  TEXT    NOT NULL
                   )
                   """;
        }

        @Override
        public String upsertNpc() {
            return """
                   INSERT INTO betternpcs_npcs
                       (uuid, name, type, world, x, y, z, yaw, pitch, spawn_by_default, data)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT(uuid) DO UPDATE SET
                       name = excluded.name,
                       type = excluded.type,
                       world = excluded.world,
                       x = excluded.x,
                       y = excluded.y,
                       z = excluded.z,
                       yaw = excluded.yaw,
                       pitch = excluded.pitch,
                       spawn_by_default = excluded.spawn_by_default,
                       data = excluded.data
                   """;
        }

        @Override
        public boolean hasNativeBooleans() {
            return false;
        }
    }

    /**
     * MySQL and MariaDB.
     *
     * @since 1.0.0
     */
    final class MySql implements SqlDialect {

        @Override
        public List<String> createSchema() {
            return List.of(
                    """
                    CREATE TABLE IF NOT EXISTS betternpcs_npcs (
                        uuid              CHAR(36)     NOT NULL PRIMARY KEY,
                        name              VARCHAR(32)  NOT NULL,
                        type              VARCHAR(64)  NOT NULL,
                        world             VARCHAR(255) NOT NULL,
                        x                 DOUBLE       NOT NULL,
                        y                 DOUBLE       NOT NULL,
                        z                 DOUBLE       NOT NULL,
                        yaw               FLOAT        NOT NULL,
                        pitch             FLOAT        NOT NULL,
                        spawn_by_default  BOOLEAN      NOT NULL DEFAULT TRUE,
                        data              LONGTEXT     NOT NULL,
                        UNIQUE KEY betternpcs_npcs_name (name),
                        KEY betternpcs_npcs_world (world)
                    ) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
                    """);
        }

        @Override
        public String createSchemaTable() {
            return """
                   CREATE TABLE IF NOT EXISTS betternpcs_schema (
                       version     INT          NOT NULL PRIMARY KEY,
                       applied_at  VARCHAR(64)  NOT NULL
                   ) DEFAULT CHARSET = utf8mb4
                   """;
        }

        @Override
        public String upsertNpc() {
            // MySQL predates the SQL standard's ON CONFLICT and has never adopted it.
            return """
                   INSERT INTO betternpcs_npcs
                       (uuid, name, type, world, x, y, z, yaw, pitch, spawn_by_default, data)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   ON DUPLICATE KEY UPDATE
                       name = VALUES(name),
                       type = VALUES(type),
                       world = VALUES(world),
                       x = VALUES(x),
                       y = VALUES(y),
                       z = VALUES(z),
                       yaw = VALUES(yaw),
                       pitch = VALUES(pitch),
                       spawn_by_default = VALUES(spawn_by_default),
                       data = VALUES(data)
                   """;
        }

        @Override
        public boolean hasNativeBooleans() {
            return true;
        }
    }

    /**
     * PostgreSQL.
     *
     * @since 1.0.0
     */
    final class PostgreSql implements SqlDialect {

        @Override
        public List<String> createSchema() {
            return List.of(
                    """
                    CREATE TABLE IF NOT EXISTS betternpcs_npcs (
                        -- Text rather than Postgres' native UUID type: that type would need an
                        -- explicit cast in every statement that touches it, and nothing here ever
                        -- treats a unique id as anything but an opaque key.
                        uuid              VARCHAR(36)  NOT NULL PRIMARY KEY,
                        name              VARCHAR(32)  NOT NULL,
                        type              VARCHAR(64)  NOT NULL,
                        world             VARCHAR(255) NOT NULL,
                        x                 DOUBLE PRECISION NOT NULL,
                        y                 DOUBLE PRECISION NOT NULL,
                        z                 DOUBLE PRECISION NOT NULL,
                        yaw               REAL         NOT NULL,
                        pitch             REAL         NOT NULL,
                        spawn_by_default  BOOLEAN      NOT NULL DEFAULT TRUE,
                        data              TEXT         NOT NULL
                    )
                    """,
                    // Postgres compares text case sensitively, so uniqueness is enforced on the
                    // lower-cased name to match how the engine looks NPCs up.
                    "CREATE UNIQUE INDEX IF NOT EXISTS betternpcs_npcs_name ON betternpcs_npcs (LOWER(name))",
                    "CREATE INDEX IF NOT EXISTS betternpcs_npcs_world ON betternpcs_npcs (world)");
        }

        @Override
        public String createSchemaTable() {
            return """
                   CREATE TABLE IF NOT EXISTS betternpcs_schema (
                       version     INTEGER      NOT NULL PRIMARY KEY,
                       applied_at  VARCHAR(64)  NOT NULL
                   )
                   """;
        }

        @Override
        public String upsertNpc() {
            return """
                   INSERT INTO betternpcs_npcs
                       (uuid, name, type, world, x, y, z, yaw, pitch, spawn_by_default, data)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT (uuid) DO UPDATE SET
                       name = EXCLUDED.name,
                       type = EXCLUDED.type,
                       world = EXCLUDED.world,
                       x = EXCLUDED.x,
                       y = EXCLUDED.y,
                       z = EXCLUDED.z,
                       yaw = EXCLUDED.yaw,
                       pitch = EXCLUDED.pitch,
                       spawn_by_default = EXCLUDED.spawn_by_default,
                       data = EXCLUDED.data
                   """;
        }

        @Override
        public boolean hasNativeBooleans() {
            return true;
        }
    }
}
