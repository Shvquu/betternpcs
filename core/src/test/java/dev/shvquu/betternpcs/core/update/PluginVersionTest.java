package dev.shvquu.betternpcs.core.update;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PluginVersionTest {

    private static PluginVersion parse(String text) {
        return PluginVersion.parse(text).orElseThrow(() ->
                new AssertionError("'" + text + "' should have parsed"));
    }

    @Nested
    @DisplayName("parsing")
    class Parsing {

        @Test
        void readsAPlainVersion() {
            assertThat(parse("1.2.3")).isEqualTo(new PluginVersion(1, 2, 3, null));
        }

        @Test
        void stripsTheTagPrefixGitHubUses() {
            // Release tags are conventionally "v1.2.3" while the artifact is "1.2.3". Both have to
            // read as the same version or every check would report an update.
            assertThat(parse("v1.2.3")).isEqualTo(parse("1.2.3"));
            assertThat(parse("V1.2.3")).isEqualTo(parse("1.2.3"));
        }

        @Test
        void readsAPreReleaseSuffix() {
            PluginVersion snapshot = parse("1.0.0-SNAPSHOT");

            assertThat(snapshot.preRelease()).isEqualTo("SNAPSHOT");
            assertThat(snapshot.isPreRelease()).isTrue();
        }

        @Test
        void ignoresBuildMetadata() {
            // Semantic versioning says build metadata carries no ordering information.
            assertThat(parse("1.2.3+build.7")).isEqualTo(parse("1.2.3"));
        }

        @ParameterizedTest
        @CsvSource({"1, 1, 0, 0", "1.2, 1, 2, 0", "1.2.3, 1, 2, 3"})
        void treatsOmittedComponentsAsZero(String text, int major, int minor, int patch) {
            assertThat(parse(text)).isEqualTo(new PluginVersion(major, minor, patch, null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "abc", "1.x.3", "1..3", "1.2.3.4", "-1.2.3", "1.2.-3"})
        void returnsEmptyForAnythingElse(String text) {
            // Empty rather than an exception: one input is a tag somebody typed into GitHub, the
            // other is whatever a fork put in gradle.properties. Neither is worth throwing over on
            // a background thread during an optional check.
            assertThat(PluginVersion.parse(text)).isEmpty();
        }

        @Test
        void returnsEmptyForNull() {
            assertThat(PluginVersion.parse(null)).isEmpty();
        }

        @Test
        void treatsABlankSuffixAsNoSuffix() {
            assertThat(parse("1.2.3-").isPreRelease()).isFalse();
        }
    }

    @Nested
    @DisplayName("ordering")
    class Ordering {

        @Test
        void comparesComponentsNumericallyNotLexically() {
            assertThat(parse("1.2.0")).isLessThan(parse("1.10.0"));
            assertThat(parse("1.0.2")).isLessThan(parse("1.0.10"));
        }

        @Test
        void sortsAPreReleaseBeforeItsRelease() {
            // The rule that makes the check useful during development: someone running
            // 1.0.0-SNAPSHOT is correctly told the released 1.0.0 is newer.
            assertThat(parse("1.0.0-SNAPSHOT")).isLessThan(parse("1.0.0"));
            assertThat(parse("1.0.0-SNAPSHOT").isOlderThan(parse("1.0.0"))).isTrue();
        }

        @Test
        void doesNotOfferAnOlderRelease() {
            assertThat(parse("2.0.0").isOlderThan(parse("1.9.9"))).isFalse();
        }

        @Test
        void doesNotOfferTheSameVersion() {
            assertThat(parse("1.2.3").isOlderThan(parse("1.2.3"))).isFalse();
            assertThat(parse("1.2.3").isOlderThan(parse("v1.2.3"))).isFalse();
        }

        @Test
        void ordersTwoPreReleasesOfTheSameVersionStably() {
            // Which of alpha and beta wins matters far less than the comparison being a total order.
            assertThat(parse("1.0.0-alpha")).isLessThan(parse("1.0.0-beta"));
            assertThat(parse("1.0.0-beta")).isGreaterThan(parse("1.0.0-alpha"));
        }

        @Test
        void treatsEqualVersionsAsEqual() {
            assertThat(parse("1.0.0-SNAPSHOT")).isEqualByComparingTo(parse("1.0.0-SNAPSHOT"));
        }
    }

    @Test
    void roundTripsThroughItsCanonicalForm() {
        for (String text : new String[] {"1.2.3", "v1.2.3", "1.0.0-SNAPSHOT", "2.0"}) {
            PluginVersion version = parse(text);
            assertThat(parse(version.toString())).isEqualTo(version);
        }
    }

    @Test
    void dropsTheTagPrefixFromItsStringForm() {
        assertThat(parse("v1.2.3")).hasToString("1.2.3");
        assertThat(parse("1.0.0-SNAPSHOT")).hasToString("1.0.0-SNAPSHOT");
    }
}
