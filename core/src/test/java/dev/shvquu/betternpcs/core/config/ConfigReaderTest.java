package dev.shvquu.betternpcs.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConfigReaderTest {

    private static ConfigReader read(String yaml) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(new StringReader(yaml));
        } catch (java.io.IOException | InvalidConfigurationException malformed) {
            throw new AssertionError("Test fixture is not valid YAML", malformed);
        }
        return ConfigReader.of(configuration);
    }

    @Nested
    @DisplayName("missing values")
    class MissingValues {

        @Test
        void fallBackToTheDefault() {
            // A file written by an older release is missing whatever the new one added. Refusing to
            // start over that would make every update a manual migration.
            ConfigReader reader = read("other: 1");

            assertThat(reader.string("absent", "fallback")).isEqualTo("fallback");
            assertThat(reader.bool("absent", true)).isTrue();
            assertThat(reader.integer("absent", 7, 0, 10)).isEqualTo(7);
            assertThat(reader.decimal("absent", 1.5, 0, 10)).isEqualTo(1.5);
        }

        @Test
        void produceAnEmptySectionRatherThanFailing() {
            ConfigReader child = read("other: 1").section("absent");

            assertThat(child.string("anything", "fallback")).isEqualTo("fallback");
        }

        @Test
        void doNotAddThemselvesToTheConfiguration() {
            // Creating the section here would write an empty block back to config.yml on the next
            // save, growing the file with every setting that was ever optional.
            ConfigReader reader = read("other: 1");

            reader.section("absent");

            assertThat(reader.section().contains("absent")).isFalse();
        }
    }

    @Nested
    @DisplayName("present but wrong values")
    class WrongValues {

        @Test
        void areRefusedRatherThanSilentlyReplaced() {
            // Bukkit's own getInt returns 0 here, which surfaces much later as a connection to
            // port 0 and an error that says nothing about config.yml.
            ConfigReader reader = read("port: not-a-number");

            assertThatThrownBy(() -> reader.integer("port", 3306, 1, 65535))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("port")
                    .hasMessageContaining("whole number");
        }

        @Test
        void areRefusedWhenOutOfRange() {
            ConfigReader reader = read("port: 70000");

            assertThatThrownBy(() -> reader.integer("port", 3306, 1, 65535))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("between 1 and 65535");
        }

        @Test
        void rejectADecimalWhereAWholeNumberIsRequired() {
            ConfigReader reader = read("count: 1.5");

            assertThatThrownBy(() -> reader.integer("count", 1, 0, 10))
                    .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void rejectANonBooleanForABooleanSetting() {
            ConfigReader reader = read("debug: maybe");

            assertThatThrownBy(() -> reader.bool("debug", false))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("true or false");
        }

        @Test
        void rejectAListWhereASingleValueIsRequired() {
            ConfigReader reader = read("host:\n  - a\n  - b");

            assertThatThrownBy(() -> reader.string("host", "localhost"))
                    .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void rejectAValueWhereASectionIsRequired() {
            ConfigReader reader = read("storage: sqlite");

            assertThatThrownBy(() -> reader.section("storage"))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("must be a section");
        }
    }

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        void readsNestedPathsAndNamesThemInErrors() {
            ConfigReader storage = read("storage:\n  mysql:\n    port: nonsense").section("storage");
            ConfigReader mysql = storage.section("mysql");

            assertThatThrownBy(() -> mysql.integer("port", 3306, 1, 65535))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("storage.mysql.port");
        }

        @Test
        void acceptsANumberWrittenAsAString() {
            // YAML turns 3306 into a number but "3306" into a string, and an administrator's quotes
            // should not change the meaning of a port.
            assertThat(read("port: '3306'").integer("port", 1, 1, 65535)).isEqualTo(3306);
        }

        @Test
        void readsAnEnumCaseInsensitivelyAndWithDashes() {
            assertThat(read("type: sqlite").enumeration("type", StorageType.MYSQL, StorageType.class))
                    .isEqualTo(StorageType.SQLITE);
            assertThat(read("type: PostgreSQL").enumeration("type", null, StorageType.class))
                    .isEqualTo(StorageType.POSTGRESQL);
        }

        @Test
        void listsTheAcceptedValuesWhenAnEnumIsWrong() {
            ConfigReader reader = read("type: oracle");

            assertThatThrownBy(() -> reader.enumeration("type", StorageType.SQLITE, StorageType.class))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("SQLITE")
                    .hasMessageContaining("MONGODB");
        }

        @Test
        void readsAStringMapInFileOrder() {
            ConfigReader reader = read("properties:\n  useSSL: 'false'\n  zeta: '1'\n  alpha: '2'");

            assertThat(reader.stringMap("properties"))
                    .containsExactly(
                            org.assertj.core.api.Assertions.entry("useSSL", "false"),
                            org.assertj.core.api.Assertions.entry("zeta", "1"),
                            org.assertj.core.api.Assertions.entry("alpha", "2"));
        }

        @Test
        void readsAnAbsentStringMapAsEmpty() {
            assertThat(read("other: 1").stringMap("properties")).isEmpty();
        }
    }

    @Test
    void turnsAValueObjectRejectionIntoANamedConfigurationError() {
        // The settings records already know what a valid value is. This turns their complaint into
        // something that points at the line to edit.
        ConfigReader reader = read("file: ../../etc/passwd");

        assertThatThrownBy(() ->
                reader.build("file", () -> new StorageSettings.Sqlite(reader.string("file", "npcs.db"))))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("file");
    }
}
