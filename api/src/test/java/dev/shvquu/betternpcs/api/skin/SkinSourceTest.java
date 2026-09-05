package dev.shvquu.betternpcs.api.skin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.shvquu.betternpcs.api.npc.property.NpcSkin;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SkinSourceTest {

    @Test
    void namesAPlayer() {
        SkinSource source = SkinSource.playerName("Notch");

        assertThat(source.kind()).isEqualTo(SkinSource.Kind.NAME);
        assertThat(source.value()).isEqualTo("Notch");
        assertThat(source.requiresLookup()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "a_name_that_is_far_too_long"})
    void rejectsAnImpossiblePlayerName(String name) {
        assertThatThrownBy(() -> SkinSource.playerName(name))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void namesAUniqueId() {
        UUID id = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        SkinSource source = SkinSource.uniqueId(id);

        assertThat(source.kind()).isEqualTo(SkinSource.Kind.UUID);
        assertThat(source.value()).isEqualTo(id.toString());
        assertThat(source.requiresLookup()).isTrue();
    }

    @Test
    void wrapsAnAlreadyResolvedTexture() {
        SkinSource source = SkinSource.texture(NpcSkin.of("value", "signature"));

        assertThat(source.kind()).isEqualTo(SkinSource.Kind.TEXTURE);
        // The whole point of this kind: it never touches the network, so it is safe on the main
        // thread and keeps working when Mojang's session servers are down.
        assertThat(source.requiresLookup()).isFalse();
    }

    @Test
    void roundTripsANameThroughItsSerialisedForm() {
        SkinSource original = SkinSource.playerName("jeb_");

        assertThat(SkinSource.of(original.kind(), original.value())).isEqualTo(original);
    }

    @Test
    void roundTripsAUniqueIdThroughItsSerialisedForm() {
        SkinSource original = SkinSource.uniqueId(UUID.randomUUID());

        assertThat(SkinSource.of(original.kind(), original.value())).isEqualTo(original);
    }

    @Test
    void refusesToRebuildATextureFromOneString() {
        // A texture needs its signature too, and silently producing an unsigned skin here would
        // render as Steve with no indication of why.
        assertThatThrownBy(() -> SkinSource.of(SkinSource.Kind.TEXTURE, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void rejectsAMalformedUniqueId() {
        assertThatThrownBy(() -> SkinSource.of(SkinSource.Kind.UUID, "not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trimsAPlayerName() {
        assertThat(SkinSource.playerName("  Notch  ").value()).isEqualTo("Notch");
    }
}
