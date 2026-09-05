package dev.shvquu.betternpcs.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import dev.shvquu.betternpcs.core.config.BetterNpcsConfig;
import dev.shvquu.betternpcs.core.i18n.LanguageManager;
import dev.shvquu.betternpcs.core.i18n.Message;
import dev.shvquu.betternpcs.core.i18n.MessageBundle;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks the files BetterNPCs ships against the code that reads them.
 *
 * <p>Every one of these failures is the kind that would otherwise be found by a server owner rather
 * than by the build: a language file that fell behind a new message, a {@code config.yml} whose
 * documented default no longer matches the code, a MiniMessage typo that renders as raw tags.
 */
class PackagedResourcesTest {

    private static YamlConfiguration loadPackaged(String resource) {
        try (InputStream stream = PackagedResourcesTest.class.getResourceAsStream("/" + resource)) {
            assertThat(stream).as("packaged resource %s", resource).isNotNull();
            YamlConfiguration configuration = new YamlConfiguration();
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                configuration.load(reader);
            }
            return configuration;
        } catch (IOException | InvalidConfigurationException failure) {
            throw new AssertionError("Packaged resource " + resource + " could not be read", failure);
        }
    }

    static List<String> bundledLocales() {
        return LanguageManager.BUNDLED_LOCALES;
    }

    @Nested
    @DisplayName("config.yml")
    class Config {

        @Test
        void parsesWithoutError() {
            assertThat(BetterNpcsConfig.load(loadPackaged("config.yml"))).isNotNull();
        }

        @Test
        void matchesTheDefaultsInCode() {
            // Two sources of truth for the same defaults drift apart silently: the documented value
            // says one thing and a server with no config.yml behaves differently. This is the check
            // that keeps them honest.
            //
            // Compared recursively rather than with isEqualTo, because StorageSettings.toString
            // deliberately hides the inactive blocks and the secrets in them — a plain equality
            // failure would print two identical-looking strings and say nothing about the field that
            // actually differs.
            assertThat(BetterNpcsConfig.load(loadPackaged("config.yml")))
                    .usingRecursiveComparison()
                    .isEqualTo(BetterNpcsConfig.defaults());
        }
    }

    @Nested
    @DisplayName("language files")
    class Languages {

        @ParameterizedTest
        @MethodSource("dev.shvquu.betternpcs.plugin.PackagedResourcesTest#bundledLocales")
        void areAllPackaged(String locale) {
            assertThat(PackagedResourcesTest.class.getResourceAsStream("/languages/" + locale + ".yml"))
                    .as("packaged language file for %s", locale)
                    .isNotNull();
        }

        @ParameterizedTest
        @MethodSource("dev.shvquu.betternpcs.plugin.PackagedResourcesTest#bundledLocales")
        void translateEveryMessage(String locale) {
            MessageBundle bundle =
                    MessageBundle.load(locale, loadPackaged("languages/" + locale + ".yml"));

            // A shipped translation that has fallen behind should fail the build, not quietly serve
            // English to the servers that chose that language.
            assertThat(bundle.missingKeys())
                    .as("untranslated keys in %s.yml", locale)
                    .isEmpty();
        }

        @ParameterizedTest
        @MethodSource("dev.shvquu.betternpcs.plugin.PackagedResourcesTest#bundledLocales")
        void containNoUnknownKeys(String locale) {
            YamlConfiguration file = loadPackaged("languages/" + locale + ".yml");

            List<String> unknown = file.getKeys(true).stream()
                    .filter(key -> !(file.get(key) instanceof org.bukkit.configuration.ConfigurationSection))
                    .filter(key -> Message.byKey(key).isEmpty())
                    .toList();

            // Usually a typo in a key, which would otherwise present as an untranslated message with
            // no obvious cause.
            assertThat(unknown).as("keys in %s.yml that no message uses", locale).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("dev.shvquu.betternpcs.plugin.PackagedResourcesTest#bundledLocales")
        void areValidMiniMessage(String locale) {
            MessageBundle bundle =
                    MessageBundle.load(locale, loadPackaged("languages/" + locale + ".yml"));

            for (Message message : Message.values()) {
                String source = bundle.text(message);
                Component rendered = MiniMessage.miniMessage().deserialize(source);
                String plain = PlainTextComponentSerializer.plainText().serialize(rendered);

                // A malformed or misspelt tag survives deserialisation as literal text, so the
                // rendered output still contains the angle brackets. Placeholders are the legitimate
                // exception and are resolved at send time, so only closing tags are checked here.
                assertThat(plain)
                        .as("%s.yml key '%s' renders a leftover closing tag", locale, message.key())
                        .doesNotContain("</");
            }
        }

        @ParameterizedTest
        @MethodSource("dev.shvquu.betternpcs.plugin.PackagedResourcesTest#bundledLocales")
        void keepEveryPlaceholderTheEnglishTextUses(String locale) {
            MessageBundle bundle =
                    MessageBundle.load(locale, loadPackaged("languages/" + locale + ".yml"));

            for (Message message : Message.values()) {
                for (String placeholder : placeholdersIn(message.englishText())) {
                    // A translation that drops <npc> produces a grammatical sentence that does not
                    // say which NPC. Nothing at runtime would notice.
                    assertThat(bundle.text(message))
                            .as("%s.yml key '%s' must keep the placeholder <%s>",
                                    locale, message.key(), placeholder)
                            .contains("<" + placeholder + ">");
                }
            }
        }
    }

    /**
     * Extracts the placeholder names from a MiniMessage template.
     *
     * <p>A placeholder is distinguished from a formatting tag by not being one BetterNPCs' own
     * messages use for formatting. Keeping that list here rather than deriving it from MiniMessage
     * is deliberate: this test should fail when a message starts using a tag nobody vetted.
     *
     * @param template the English text
     * @return the placeholder names, without angle brackets
     */
    private static List<String> placeholdersIn(String template) {
        List<String> formattingTags = List.of(
                "red", "green", "yellow", "gray", "dark_gray", "white", "reset", "bold",
                "gradient", "click", "hover");

        return java.util.regex.Pattern.compile("<([a-z_]+)>")
                .matcher(template)
                .results()
                .map(match -> match.group(1))
                .filter(tag -> !formattingTags.contains(tag))
                .distinct()
                .toList();
    }
}
