package dev.shvquu.betternpcs.core.version;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VersionAdapterResolverTest {

    private final VersionAdapterResolver resolver = VersionAdapterResolver.withDefaults();

    @ParameterizedTest
    @CsvSource({
        "1.21.4,  v1_21_4",
        "1.21.5,  v1_21_5",
        "1.21.6,  v1_21_8",
        "1.21.7,  v1_21_8",
        "1.21.8,  v1_21_8",
        "1.21.9,  v1_21_11",
        "1.21.10, v1_21_11",
        "1.21.11, v1_21_11",
        "26.1,    v26_1",
        "26.1.2,  v26_1",
        "26.2,    v26_2",
    })
    void mapsEachSupportedVersionToItsAdapter(String version, String expectedModule) {
        MinecraftVersion parsed = MinecraftVersion.parse(version);

        assertThat(resolver.resolve(parsed))
                .hasValueSatisfying(registration ->
                        assertThat(registration.adapterClassName()).contains("." + expectedModule + "."));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "1.20.6",  // below the supported range
        "1.21.3",  // below the supported range
        "26.3",    // released after this build — refuse rather than guess
        "27.1",    // a future year
    })
    void reportsUnsupportedVersionsInsteadOfGuessing(String version) {
        // Silently falling back to "the newest adapter we have" would produce a plugin that appears
        // to start and then sends malformed packets. An honest refusal is the safer failure.
        assertThat(resolver.resolve(MinecraftVersion.parse(version))).isEmpty();
    }

    @Test
    void registrationsAreOrderedAndDoNotOverlap() {
        List<VersionAdapterResolver.Registration> registrations = resolver.registrations();

        assertThat(registrations).isNotEmpty();
        for (int i = 0; i < registrations.size(); i++) {
            VersionAdapterResolver.Registration current = registrations.get(i);
            assertThat(current.minInclusive())
                    .as("range %s must be non-empty", current)
                    .isLessThan(current.maxExclusive());

            if (i > 0) {
                assertThat(registrations.get(i - 1).maxExclusive())
                        .as("range %s must not overlap the previous one", current)
                        .isLessThanOrEqualTo(current.minInclusive());
            }
        }
    }

    @Test
    void describesTheSupportedRangeForErrorMessages() {
        assertThat(resolver.supportedRangeDescription()).contains("1.21.4").contains("26.2");
    }
}
