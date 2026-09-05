package dev.shvquu.betternpcs.api.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ActionDefinitionTest {

    @Test
    void parsesTypeAndArgument() {
        ActionDefinition action = ActionDefinition.parse("message: Hello there");

        assertThat(action.type()).isEqualTo("message");
        assertThat(action.argument()).isEqualTo("Hello there");
    }

    @Test
    void splitsOnTheFirstColonOnly() {
        // MiniMessage tags and namespaced keys are full of colons; splitting on the last one, or on
        // every one, would mangle every interesting argument there is.
        ActionDefinition action =
                ActionDefinition.parse("message: <gradient:#00c6ff:#0072ff>Welcome</gradient>");

        assertThat(action.type()).isEqualTo("message");
        assertThat(action.argument()).isEqualTo("<gradient:#00c6ff:#0072ff>Welcome</gradient>");
    }

    @Test
    void normalisesTheTypeToLowerCase() {
        assertThat(ActionDefinition.parse("MESSAGE: hi").type()).isEqualTo("message");
    }

    @Test
    void acceptsAnEmptyArgument() {
        ActionDefinition action = ActionDefinition.parse("close:");

        assertThat(action.type()).isEqualTo("close");
        assertThat(action.argument()).isEmpty();
    }

    @Test
    void rejectsALineWithoutAColon() {
        // Tolerating this would turn a typo into an action no handler serves, discovered only when
        // a player clicks the NPC.
        assertThatThrownBy(() -> ActionDefinition.parse("message hello"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type: argument");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "with space", "with:colon"})
    void rejectsAnInvalidType(String type) {
        assertThatThrownBy(() -> new ActionDefinition(type, "argument"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundTripsThroughSerialisation() {
        ActionDefinition original = ActionDefinition.parse("console: give <player_name> diamond 1");

        assertThat(ActionDefinition.parse(original.serialize())).isEqualTo(original);
    }

    @Test
    void roundTripsAnEmptyArgument() {
        ActionDefinition original = ActionDefinition.of("close");

        assertThat(ActionDefinition.parse(original.serialize())).isEqualTo(original);
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(ActionDefinition.parse("  message :   hi   ").type()).isEqualTo("message");
        assertThat(ActionDefinition.parse("message:    hi   ").argument()).isEqualTo("hi");
    }
}
