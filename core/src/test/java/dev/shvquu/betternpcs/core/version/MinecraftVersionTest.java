package dev.shvquu.betternpcs.core.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MinecraftVersionTest {

    @Nested
    @DisplayName("parsing")
    class Parsing {

        @Test
        void parsesTheLegacyScheme() {
            MinecraftVersion version = MinecraftVersion.parse("1.21.4");

            assertThat(version.scheme()).isEqualTo(MinecraftVersion.Scheme.LEGACY);
            assertThat(version.components()).containsExactly(1, 21, 4);
        }

        @Test
        void parsesTheDropScheme() {
            // Mojang switched to year.drop.hotfix with 26.1. A parser that assumes a leading "1."
            // silently misreads every version released from 2026 onwards.
            MinecraftVersion version = MinecraftVersion.parse("26.2");

            assertThat(version.scheme()).isEqualTo(MinecraftVersion.Scheme.DROP);
            assertThat(version.components()).containsExactly(26, 2);
        }

        @Test
        void parsesADropHotfix() {
            assertThat(MinecraftVersion.parse("26.1.2").components()).containsExactly(26, 1, 2);
        }

        @Test
        void stripsAPreReleaseSuffix() {
            MinecraftVersion version = MinecraftVersion.parse("1.21.9-rc1");

            assertThat(version.components()).containsExactly(1, 21, 9);
            assertThat(version.raw()).isEqualTo("1.21.9-rc1");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "abc", "1.x.4", "1..4", "-1.2", "1.2.", "."})
        void rejectsGarbage(String input) {
            assertThatThrownBy(() -> MinecraftVersion.parse(input))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNull() {
            assertThatThrownBy(() -> MinecraftVersion.parse(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("ordering")
    class Ordering {

        @Test
        void comparesComponentsNumericallyNotLexically() {
            // The bug this guards against: "1.21.4" > "1.21.11" under string comparison.
            assertThat(MinecraftVersion.parse("1.21.4"))
                    .isLessThan(MinecraftVersion.parse("1.21.11"));
        }

        @Test
        void ordersEveryLegacyVersionBelowEveryDropVersion() {
            assertThat(MinecraftVersion.parse("1.21.11"))
                    .isLessThan(MinecraftVersion.parse("26.1"));
        }

        @Test
        void ordersWithinTheDropScheme() {
            assertThat(MinecraftVersion.parse("26.1"))
                    .isLessThan(MinecraftVersion.parse("26.1.2"));
            assertThat(MinecraftVersion.parse("26.1.2"))
                    .isLessThan(MinecraftVersion.parse("26.2"));
        }

        @Test
        void treatsOmittedTrailingComponentsAsZero() {
            assertThat(MinecraftVersion.parse("1.21"))
                    .isEqualByComparingTo(MinecraftVersion.parse("1.21.0"));
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        void ignoresTheRawStringAndTrailingZeros() {
            assertThat(MinecraftVersion.parse("26.2")).isEqualTo(MinecraftVersion.parse("26.2.0"));
            assertThat(MinecraftVersion.parse("26.2")).hasSameHashCodeAs(MinecraftVersion.parse("26.2.0"));
        }

        @Test
        void distinguishesDifferentVersions() {
            assertThat(MinecraftVersion.parse("26.2")).isNotEqualTo(MinecraftVersion.parse("26.1"));
        }
    }

    @Test
    void rendersTheNormalisedFormInToString() {
        assertThat(MinecraftVersion.parse("1.21.9-rc1")).hasToString("1.21.9");
    }
}
