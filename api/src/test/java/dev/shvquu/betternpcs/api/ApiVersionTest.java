package dev.shvquu.betternpcs.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApiVersionTest {

    @Test
    void parsesADottedVersion() {
        assertThat(ApiVersion.parse("2.13.4")).isEqualTo(new ApiVersion(2, 13, 4));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0", "1.0.0.0", "", "1.x.0", "abc"})
    void rejectsAnythingElse(String input) {
        assertThatThrownBy(() -> ApiVersion.parse(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeComponents() {
        assertThatThrownBy(() -> new ApiVersion(1, -1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordersNumericallyNotLexically() {
        assertThat(ApiVersion.parse("1.2.0")).isLessThan(ApiVersion.parse("1.10.0"));
    }

    @Test
    void acceptsTheSameMajorAndANewerMinor() {
        assertThat(new ApiVersion(1, 4, 2).isCompatibleWith(1, 2)).isTrue();
        assertThat(new ApiVersion(1, 2, 0).isCompatibleWith(1, 2)).isTrue();
    }

    @Test
    void refusesAnOlderMinor() {
        // The dependent plugin uses API added in 1.4; running against 1.2 would fail at the first
        // call with a NoSuchMethodError somewhere unrelated.
        assertThat(new ApiVersion(1, 2, 0).isCompatibleWith(1, 4)).isFalse();
    }

    @Test
    void refusesADifferentMajorInEitherDirection() {
        assertThat(new ApiVersion(2, 0, 0).isCompatibleWith(1, 0)).isFalse();
        assertThat(new ApiVersion(1, 9, 9).isCompatibleWith(2, 0)).isFalse();
    }

    @Test
    void roundTripsThroughItsStringForm() {
        ApiVersion version = new ApiVersion(3, 1, 4);

        assertThat(ApiVersion.parse(version.toString())).isEqualTo(version);
    }

    @Test
    void declaresTheVersionThisArtifactImplements() {
        assertThat(ApiVersion.CURRENT.major()).isPositive();
    }
}
