package dev.shvquu.betternpcs.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StorageSettingsTest {

    private static StorageSettings.Jdbc jdbc(String password) {
        return new StorageSettings.Jdbc(
                "db.internal", 3306, "npcs", "npcuser", password, 10, 10_000L, Map.of());
    }

    @Nested
    @DisplayName("secrets")
    class Secrets {

        @Test
        void neverAppearInAJdbcDescription() {
            // These objects end up in debug logs and in crash reports pasted into public issue
            // trackers. A password reaching one of those is a real incident, not a cosmetic bug.
            String description = jdbc("hunter2").toString();

            assertThat(description).doesNotContain("hunter2");
            assertThat(description).contains(StorageSettings.REDACTED);
            assertThat(description).contains("db.internal:3306/npcs");
        }

        @Test
        void neverAppearInAMongoDescription() {
            StorageSettings.Mongo mongo = new StorageSettings.Mongo(
                    "mongodb://admin:s3cret@cluster.internal:27017/?retryWrites=true", "npcs");

            assertThat(mongo.toString()).doesNotContain("s3cret");
            assertThat(mongo.toString()).doesNotContain("admin");
            assertThat(mongo.describeTarget()).isEqualTo(
                    StorageSettings.REDACTED + "@cluster.internal:27017/npcs");
        }

        @Test
        void neverAppearInTheAggregateDescription() {
            StorageSettings settings = new StorageSettings(
                    StorageType.MYSQL,
                    new StorageSettings.Sqlite("npcs.db"),
                    jdbc("hunter2"),
                    jdbc("other"),
                    new StorageSettings.Mongo("mongodb://admin:s3cret@host:27017", "npcs"));

            assertThat(settings.toString()).doesNotContain("hunter2").doesNotContain("s3cret");
        }

        @Test
        void areStillUsableAfterRedaction() {
            assertThat(jdbc("hunter2").password()).isEqualTo("hunter2");
            assertThat(jdbc("hunter2").hasPassword()).isTrue();
            assertThat(jdbc("").hasPassword()).isFalse();
        }

        @Test
        void describesAConnectionStringWithoutCredentialsUnchanged() {
            StorageSettings.Mongo mongo =
                    new StorageSettings.Mongo("mongodb://localhost:27017", "npcs");

            assertThat(mongo.describeTarget()).isEqualTo("localhost:27017/npcs");
        }
    }

    @Nested
    @DisplayName("SQLite file name")
    class SqliteFileName {

        @ParameterizedTest
        @ValueSource(strings = {
            "../npcs.db",
            "../../etc/passwd",
            "/etc/passwd",
            "\\windows\\system32",
            "C:/npcs.db",
        })
        void refusesAnythingThatLeavesThePluginFolder(String fileName) {
            // The value is joined onto the data folder path. A traversal here would let a mistyped
            // or malicious config point the database somewhere else on the disk entirely.
            assertThatThrownBy(() -> new StorageSettings.Sqlite(fileName))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void acceptsAPlainFileName() {
            assertThat(new StorageSettings.Sqlite(" npcs.db ").fileName()).isEqualTo("npcs.db");
        }

        @Test
        void refusesABlankName() {
            assertThatThrownBy(() -> new StorageSettings.Sqlite("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        void refusesAPortOutsideTheUsableRange() {
            assertThatThrownBy(() -> new StorageSettings.Jdbc(
                    "host", 0, "db", "user", "", 10, 10_000L, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new StorageSettings.Jdbc(
                    "host", 70000, "db", "user", "", 10, 10_000L, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesATimeoutHikariWouldRejectAnyway() {
            // Catching it here names the setting; letting it through produces a HikariCP exception
            // that talks about "connectionTimeout" and not about config.yml.
            assertThatThrownBy(() -> new StorageSettings.Jdbc(
                    "host", 3306, "db", "user", "", 10, 100L, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("250");
        }

        @Test
        void treatsANullPasswordAsNoPassword() {
            assertThat(jdbc(null).password()).isEmpty();
        }

        @Test
        void refusesAConnectionStringThatIsNotAMongoUri() {
            assertThatThrownBy(() -> new StorageSettings.Mongo("localhost:27017", "npcs"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mongodb://");
        }

        @Test
        void doesNotRepeatTheConnectionStringInItsRejection() {
            // The rejected value could itself be the secret.
            assertThatThrownBy(() ->
                    new StorageSettings.Mongo("postgres://admin:s3cret@host/db", "npcs"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining("s3cret");
        }

        @Test
        void copiesTheDriverProperties() {
            Map<String, String> mutable = new java.util.LinkedHashMap<>(Map.of("useSSL", "false"));
            StorageSettings.Jdbc settings = new StorageSettings.Jdbc(
                    "host", 3306, "db", "user", "", 10, 10_000L, mutable);

            mutable.put("added", "later");

            assertThat(settings.properties()).containsOnlyKeys("useSSL");
        }
    }

    @Nested
    @DisplayName("active backend")
    class ActiveBackend {

        private StorageSettings settingsFor(StorageType type) {
            return new StorageSettings(
                    type,
                    new StorageSettings.Sqlite("npcs.db"),
                    jdbc("mysql-password"),
                    jdbc("postgres-password"),
                    new StorageSettings.Mongo("mongodb://localhost:27017", "npcs"));
        }

        @Test
        void mariaDbSharesTheMysqlBlock() {
            // The two differ only in the driver. Making an administrator duplicate the block would
            // be busywork with an obvious failure mode: editing one and not the other.
            assertThat(settingsFor(StorageType.MARIADB).activeJdbc())
                    .isEqualTo(settingsFor(StorageType.MYSQL).activeJdbc());
        }

        @Test
        void postgresUsesItsOwnBlock() {
            assertThat(settingsFor(StorageType.POSTGRESQL).activeJdbc().password())
                    .isEqualTo("postgres-password");
        }

        @Test
        void refusesToProduceJdbcSettingsForANonJdbcBackend() {
            assertThatThrownBy(() -> settingsFor(StorageType.SQLITE).activeJdbc())
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> settingsFor(StorageType.MONGODB).activeJdbc())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void classifiesEveryBackend() {
        assertThat(StorageType.SQLITE.isSql()).isTrue();
        assertThat(StorageType.MONGODB.isSql()).isFalse();
        assertThat(StorageType.parse("  postgresql  ")).contains(StorageType.POSTGRESQL);
        assertThat(StorageType.parse("oracle")).isEmpty();
        assertThat(StorageType.supportedValues()).contains("MARIADB");
    }
}
