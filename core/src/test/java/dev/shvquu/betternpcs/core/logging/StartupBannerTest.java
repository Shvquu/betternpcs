package dev.shvquu.betternpcs.core.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StartupBannerTest {

    private static StartupBanner.Details details() {
        return new StartupBanner.Details(
                "BetterNPCs",
                "1.0.0",
                "1.0.0",
                "1.21.4",
                "Paper 1.21.4-232",
                "21.0.5",
                "SQLITE -> npcs.db",
                "en_US",
                "Paper 1.21.4 (Mojang-mapped)",
                false);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void drawsARectangularBox(boolean unicode) {
        // A box whose borders do not line up looks broken, and adding one field is all it takes to
        // break it. Sizing the box from its contents is only correct if this stays true.
        List<String> lines = StartupBanner.render(details(), unicode);

        assertThat(lines).isNotEmpty();
        int width = lines.get(0).length();
        assertThat(lines).allSatisfy(line -> assertThat(line).hasSize(width));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void staysRectangularWhenAFieldIsUnusuallyLong(boolean unicode) {
        StartupBanner.Details wide = new StartupBanner.Details(
                "BetterNPCs", "1.0.0", "1.0.0", "1.21.4",
                "Paper version git-Paper-232 (MC: 1.21.4) with a very long build description",
                "21.0.5", "POSTGRESQL -> database.internal.example.com:5432/betternpcs",
                "en_US", "Paper 1.21.4 (Mojang-mapped)", true);

        List<String> lines = StartupBanner.render(wide, unicode);

        int width = lines.get(0).length();
        assertThat(lines).allSatisfy(line -> assertThat(line).hasSize(width));
    }

    @Test
    void reportsEverythingNeededToDiagnoseAProblem() {
        // Each of these is a question that would otherwise cost a round trip with the server owner.
        String banner = String.join("\n", StartupBanner.render(details(), true));

        assertThat(banner)
                .contains("BetterNPCs")
                .contains("1.0.0")
                .contains("1.21.4")
                .contains("Paper 1.21.4-232")
                .contains("21.0.5")
                .contains("SQLITE")
                .contains("en_US")
                .contains("Mojang-mapped");
    }

    @Test
    void statesWhetherDebugLoggingIsOn() {
        assertThat(String.join("\n", StartupBanner.render(details(), true))).contains("Debug:");

        StartupBanner.Details debugging = new StartupBanner.Details(
                "BetterNPCs", "1.0.0", "1.0.0", "1.21.4", "Paper", "21", "SQLITE", "en_US", "x", true);

        assertThat(String.join("\n", StartupBanner.render(debugging, true)))
                .containsPattern("Debug:\\s+on");
    }

    @Test
    void usesOnlyAsciiInTheAsciiStyle() {
        // The point of the fallback: a legacy Windows code page renders anything outside ASCII as
        // question marks, and a wall of those is a worse first impression than plain text.
        String banner = String.join("\n", StartupBanner.render(details(), false));

        assertThat(banner.chars().allMatch(character -> character < 128))
                .as("ASCII banner contains a non-ASCII character")
                .isTrue();
    }

    @Test
    void usesTheNameItWasGivenSoAForkIdentifiesItself() {
        StartupBanner.Details renamed = new StartupBanner.Details(
                "MyNPCs", "2.0.0", "1.0.0", "26.2", "Paper", "25", "MONGODB", "de_DE", "x", false);

        assertThat(String.join("\n", StartupBanner.render(renamed, true))).contains("MyNPCs 2.0.0");
    }

    @Test
    void rejectsMissingDetails() {
        assertThatThrownBy(() -> new StartupBanner.Details(
                null, "1.0.0", "1.0.0", "1.21.4", "Paper", "21", "SQLITE", "en_US", "x", false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void answersWhetherTheConsoleCanDrawABox() {
        // No assertion on the value: it depends on the machine. What matters is that probing the
        // console encoding cannot itself throw, because it runs before anything else at startup.
        assertThat(StartupBanner.consoleSupportsBoxDrawing()).isIn(true, false);
    }
}
