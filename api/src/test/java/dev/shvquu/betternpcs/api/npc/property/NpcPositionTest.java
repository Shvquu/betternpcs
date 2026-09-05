package dev.shvquu.betternpcs.api.npc.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NpcPositionTest {

    @Nested
    @DisplayName("rotation normalisation")
    class RotationNormalisation {

        @ParameterizedTest
        @CsvSource({
            "0,    0",
            "90,   90",
            "180, -180",
            "270, -90",
            "360,  0",
            "450,  90",
            "-90, -90",
            "-180,-180",
            "-270, 90",
            "-360, 0",
        })
        void wrapsYawIntoTheProtocolRange(float input, float expected) {
            assertThat(new NpcPosition("world", 0, 0, 0, input, 0).yaw()).isEqualTo(expected);
        }

        @Test
        void treatsAFullTurnAsNoTurnForEquality() {
            // The bug this guards against: an NPC re-sent every tick because a yaw of 360 and a yaw
            // of 0 compared unequal, producing a rotation packet that changes nothing.
            assertThat(new NpcPosition("world", 1, 2, 3, 360.0f, 0))
                    .isEqualTo(new NpcPosition("world", 1, 2, 3, 0.0f, 0));
        }

        @Test
        void collapsesNegativeZeroYaw() {
            assertThat(new NpcPosition("world", 0, 0, 0, -0.0f, 0))
                    .isEqualTo(new NpcPosition("world", 0, 0, 0, 0.0f, 0));
        }

        @ParameterizedTest
        @CsvSource({"90, 90", "120, 90", "-90, -90", "-120, -90", "45, 45"})
        void clampsPitchToWhatTheProtocolCanExpress(float input, float expected) {
            assertThat(new NpcPosition("world", 0, 0, 0, 0, input).pitch()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        void rejectsABlankWorld() {
            assertThatThrownBy(() -> new NpcPosition("  ", 0, 0, 0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        void rejectsANullWorld() {
            assertThatThrownBy(() -> new NpcPosition(null, 0, 0, 0, 0, 0))
                    .isInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest
        @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
        void rejectsNonFiniteCoordinates(double coordinate) {
            // A NaN coordinate would serialise, load back, and then place the NPC nowhere at all.
            assertThatThrownBy(() -> new NpcPosition("world", coordinate, 0, 0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new NpcPosition("world", 0, coordinate, 0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new NpcPosition("world", 0, 0, coordinate, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNonFiniteRotation() {
            assertThatThrownBy(() -> new NpcPosition("world", 0, 0, 0, Float.NaN, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new NpcPosition("world", 0, 0, 0, 0, Float.NaN))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("derivation")
    class Derivation {

        private final NpcPosition position = new NpcPosition("world", 10, 64, -20, 90, 15);

        @Test
        void withCoordinatesKeepsWorldAndRotation() {
            NpcPosition moved = position.withCoordinates(1, 2, 3);

            assertThat(moved.world()).isEqualTo("world");
            assertThat(moved.x()).isEqualTo(1);
            assertThat(moved.yaw()).isEqualTo(90);
            assertThat(moved.pitch()).isEqualTo(15);
        }

        @Test
        void withRotationKeepsCoordinates() {
            NpcPosition turned = position.withRotation(-45, -10);

            assertThat(turned.x()).isEqualTo(10);
            assertThat(turned.yaw()).isEqualTo(-45);
        }

        @Test
        void offsetAddsToEachAxis() {
            assertThat(position.offset(1, -2, 3))
                    .isEqualTo(new NpcPosition("world", 11, 62, -17, 90, 15));
        }

        @Test
        void withWorldKeepsEverythingElse() {
            assertThat(position.withWorld("nether").world()).isEqualTo("nether");
            assertThat(position.withWorld("nether").y()).isEqualTo(64);
        }
    }

    @Nested
    @DisplayName("distance")
    class Distance {

        @Test
        void measuresWithinAWorld() {
            NpcPosition from = NpcPosition.of("world", 0, 0, 0);
            NpcPosition to = NpcPosition.of("world", 3, 0, 4);

            assertThat(from.distanceSquaredTo(to)).isCloseTo(25.0, within(1e-9));
        }

        @Test
        void reportsAnUnreachableDistanceAcrossWorlds() {
            // Returning 0 or a real number here would make an NPC in the nether count as "nearby"
            // for a player in the overworld standing at the same coordinates.
            NpcPosition overworld = NpcPosition.of("world", 0, 0, 0);
            NpcPosition nether = NpcPosition.of("world_nether", 0, 0, 0);

            assertThat(overworld.distanceSquaredTo(nether)).isEqualTo(Double.MAX_VALUE);
        }

        @Test
        void comparesWorldsByName() {
            assertThat(NpcPosition.of("world", 0, 0, 0).isSameWorld(NpcPosition.of("world", 9, 9, 9)))
                    .isTrue();
            assertThat(NpcPosition.of("world", 0, 0, 0).isSameWorld(NpcPosition.of("other", 0, 0, 0)))
                    .isFalse();
        }
    }

    @Test
    void rendersACompactDescription() {
        assertThat(new NpcPosition("world", 100, 64, 100, 90, 0))
                .asString()
                .contains("world")
                .contains("100")
                .contains("64");
    }
}
