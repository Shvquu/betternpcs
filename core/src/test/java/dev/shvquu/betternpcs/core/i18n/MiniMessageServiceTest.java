package dev.shvquu.betternpcs.core.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiniMessageServiceTest {

    @TempDir
    Path languagesFolder;

    private LanguageManager languages;

    @BeforeEach
    void setUp() throws IOException {
        languages = new LanguageManager(
                languagesFolder, resource -> null, Logger.getLogger(MiniMessageServiceTest.class.getName()));
        languages.load("en_US", false);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Nested
    @DisplayName("placeholder injection")
    class PlaceholderInjection {

        @Test
        void cannotIntroduceAClickEvent() {
            // The attack this exists to stop: a player renames themselves to something tag-shaped,
            // and a message containing their name becomes a clickable command for whoever reads it.
            PlaceholderExpander hostile =
                    (recipient, token) -> "<click:run_command:'/op attacker'>victim</click>";
            MessageService service = new MiniMessageService(languages, hostile);

            Component rendered = service.renderRaw(null, "<green>Hello, %player_name%!");

            assertThat(clickEventsIn(rendered)).isEmpty();
            assertThat(plain(rendered)).contains("<click:run_command:'/op attacker'>");
        }

        @Test
        void cannotIntroduceFormatting() {
            PlaceholderExpander hostile = (recipient, token) -> "<red><bold>SHOUTING";
            MessageService service = new MiniMessageService(languages, hostile);

            Component rendered = service.renderRaw(null, "Hello, %player_name%!");

            assertThat(plain(rendered)).isEqualTo("Hello, <red><bold>SHOUTING!");
        }

        @Test
        void leavesTheTemplatesOwnFormattingIntact() {
            // The other half of the requirement: escaping the whole line would be safe but would
            // also destroy the colours the language file legitimately asks for.
            PlaceholderExpander expander = (recipient, token) -> "Steve";
            MessageService service = new MiniMessageService(languages, expander);

            Component rendered = service.renderRaw(null, "<green>Hello, %player_name%!");

            assertThat(plain(rendered)).isEqualTo("Hello, Steve!");
            assertThat(rendered.color()).isEqualTo(net.kyori.adventure.text.format.NamedTextColor.GREEN);
        }
    }

    @Nested
    @DisplayName("token matching")
    class TokenMatching {

        @Test
        void expandsEveryTokenInALine() {
            PlaceholderExpander expander = (recipient, token) -> switch (token) {
                case "%player_name%" -> "Steve";
                case "%server_online%" -> "42";
                default -> token;
            };
            MessageService service = new MiniMessageService(languages, expander);

            assertThat(plain(service.renderRaw(null, "%player_name% of %server_online%")))
                    .isEqualTo("Steve of 42");
        }

        @Test
        void leavesAnUnclaimedTokenVisible() {
            // Blanking it would silently eat part of the message and leave no clue that an expansion
            // is missing.
            MessageService service = new MiniMessageService(languages, PlaceholderExpander.none());

            assertThat(plain(service.renderRaw(null, "Ping: %player_ping%")))
                    .isEqualTo("Ping: %player_ping%");
        }

        @Test
        void doesNotTreatProseBetweenPercentSignsAsAToken() {
            PlaceholderExpander recording = (recipient, token) -> "EXPANDED";
            MessageService service = new MiniMessageService(languages, recording);

            assertThat(plain(service.renderRaw(null, "50% done, 20% left")))
                    .isEqualTo("50% done, 20% left");
        }

        @Test
        void doesNotCallTheExpanderWhenThereIsNoPercentSign() {
            PlaceholderExpander exploding = (recipient, token) -> {
                throw new AssertionError("The expander must not be consulted for a line with no token");
            };
            MessageService service = new MiniMessageService(languages, exploding);

            assertThat(plain(service.renderRaw(null, "<green>No placeholders here")))
                    .isEqualTo("No placeholders here");
        }

        @Test
        void treatsADollarInAnExpandedValueLiterally() {
            // Regex replacement treats '$' as a group reference. Without quoting, a player named
            // "$1" would blow up with an IndexOutOfBoundsException deep inside the renderer.
            PlaceholderExpander expander = (recipient, token) -> "$1\\test";
            MessageService service = new MiniMessageService(languages, expander);

            assertThat(plain(service.renderRaw(null, "Name: %player_name%")))
                    .isEqualTo("Name: $1\\test");
        }
    }

    @Nested
    @DisplayName("messages")
    class Messages {

        private MessageService service;

        @BeforeEach
        void createService() {
            // Built here rather than in a field initialiser: a nested class is constructed before
            // the outer @BeforeEach runs, so `languages` would still be null.
            service = new MiniMessageService(languages, PlaceholderExpander.none());
        }

        @Test
        void substitutesItsOwnPlaceholders() {
            Component rendered = service.render(
                    Message.NPC_NOT_FOUND, Placeholders.text("npc", "shopkeeper"));

            assertThat(plain(rendered)).contains("shopkeeper");
        }

        @Test
        void insertsSubstitutedValuesAsLiteralText() {
            // Same rule as for external placeholders, enforced by Placeholders rather than here.
            Component rendered = service.render(
                    Message.NPC_NOT_FOUND,
                    Placeholders.text("npc", "<click:run_command:'/op me'>x</click>"));

            assertThat(clickEventsIn(rendered)).isEmpty();
        }

        @Test
        void fallsBackToEnglishForAnUntranslatedKey() {
            assertThat(plain(service.render(Message.NO_PERMISSION)))
                    .isEqualTo("You do not have permission to do that.");
        }

        @Test
        void rendersThePrefix() {
            assertThat(plain(service.prefix(null))).contains("BetterNPCs");
        }

        @Test
        void roundsDecimalsForReadability() {
            Component rendered = service.renderRaw(null, "<value>", Placeholders.decimal("value", 1.0 / 3));

            assertThat(plain(rendered)).isEqualTo("0.33");
        }
    }

    @Nested
    @DisplayName("language selection")
    class LanguageSelection {

        @Test
        void usesAFileFromTheLanguagesFolder() throws IOException {
            Files.writeString(
                    languagesFolder.resolve("de_DE.yml"),
                    "general:\n  no-permission: \"Keine Berechtigung.\"\n",
                    StandardCharsets.UTF_8);
            languages.load("de_DE", false);

            MessageService service = new MiniMessageService(languages, PlaceholderExpander.none());

            assertThat(plain(service.render(Message.NO_PERMISSION))).isEqualTo("Keine Berechtigung.");
        }

        @Test
        void fallsBackToBuiltInEnglishWhenTheConfiguredFileIsMissing() throws IOException {
            // Refusing to start would be a harsh answer to a typo in one setting, and there is a
            // perfectly good English bundle compiled in.
            languages.load("nonexistent_LOCALE", false);

            MessageService service = new MiniMessageService(languages, PlaceholderExpander.none());

            assertThat(plain(service.render(Message.NO_PERMISSION)))
                    .isEqualTo("You do not have permission to do that.");
        }

        @Test
        void ignoresAMalformedFileWithoutLosingTheOthers() throws IOException {
            Files.writeString(languagesFolder.resolve("broken.yml"), "\tthis: is not: yaml\n");
            Files.writeString(
                    languagesFolder.resolve("de_DE.yml"),
                    "general:\n  no-permission: \"Keine Berechtigung.\"\n",
                    StandardCharsets.UTF_8);

            languages.load("de_DE", false);

            assertThat(languages.availableLocales()).contains("de_DE").doesNotContain("broken");
        }

        @Test
        void matchesALocaleCaseInsensitively() throws IOException {
            Files.writeString(
                    languagesFolder.resolve("de_DE.yml"),
                    "general:\n  no-permission: \"Keine Berechtigung.\"\n",
                    StandardCharsets.UTF_8);
            languages.load("DE_de", false);

            assertThat(languages.defaultBundle().locale()).isEqualTo("de_DE");
        }

        @Test
        void fallsBackFromACountryToItsLanguage() throws IOException {
            Files.writeString(
                    languagesFolder.resolve("de_DE.yml"),
                    "general:\n  no-permission: \"Keine Berechtigung.\"\n",
                    StandardCharsets.UTF_8);
            languages.load("de_DE", true);

            // A player whose client is set to Austrian German should get the German file rather than
            // English, without the plugin needing a file per country.
            assertThat(languages.bundleForClient(java.util.Locale.forLanguageTag("de-AT")).locale())
                    .isEqualTo("de_DE");
        }

        @Test
        void ignoresTheClientLocaleWhenTheSettingIsOff() throws IOException {
            Files.writeString(
                    languagesFolder.resolve("de_DE.yml"),
                    "general:\n  no-permission: \"Keine Berechtigung.\"\n",
                    StandardCharsets.UTF_8);
            Files.writeString(
                    languagesFolder.resolve("en_US.yml"),
                    "general:\n  no-permission: \"No permission.\"\n",
                    StandardCharsets.UTF_8);
            languages.load("en_US", false);

            assertThat(languages.bundleForClient(java.util.Locale.GERMANY).locale()).isEqualTo("en_US");
        }
    }

    @Test
    void writesPackagedLanguageFilesThatAreMissing() throws IOException {
        LanguageManager manager = new LanguageManager(
                languagesFolder,
                resource -> resource.endsWith("en_US.yml")
                        ? streamOf("general:\n  no-permission: \"Packaged.\"\n")
                        : null,
                Logger.getLogger("test"));

        manager.load("en_US", false);

        assertThat(languagesFolder.resolve("en_US.yml")).exists();
        assertThat(manager.defaultBundle().text(Message.NO_PERMISSION)).isEqualTo("Packaged.");
    }

    @Test
    void doesNotOverwriteAnEditedLanguageFile() throws IOException {
        // The whole point of writing the files only when missing: a server owner's edits must
        // survive an update.
        Files.writeString(
                languagesFolder.resolve("en_US.yml"),
                "general:\n  no-permission: \"Edited by the server owner.\"\n",
                StandardCharsets.UTF_8);

        LanguageManager manager = new LanguageManager(
                languagesFolder,
                resource -> streamOf("general:\n  no-permission: \"Packaged.\"\n"),
                Logger.getLogger("test"));
        manager.load("en_US", false);

        assertThat(manager.defaultBundle().text(Message.NO_PERMISSION))
                .isEqualTo("Edited by the server owner.");
    }

    private static InputStream streamOf(String content) {
        return new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static java.util.List<ClickEvent> clickEventsIn(Component component) {
        java.util.List<ClickEvent> found = new java.util.ArrayList<>();
        collectClickEvents(component, found);
        return found;
    }

    private static void collectClickEvents(Component component, java.util.List<ClickEvent> into) {
        ClickEvent event = component.clickEvent();
        if (event != null) {
            into.add(event);
        }
        component.children().forEach(child -> collectClickEvents(child, into));
    }
}
