package dev.shvquu.betternpcs.core.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.shvquu.betternpcs.api.BetterNPCsApi;
import dev.shvquu.betternpcs.api.extension.ExtensionManager;
import dev.shvquu.betternpcs.api.extension.NpcExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DefaultExtensionManagerTest {

    /** Records the order things happened in, which is what most of these tests are about. */
    private List<String> events;

    private DefaultExtensionManager manager;

    @BeforeEach
    void setUp() {
        events = new ArrayList<>();
        Logger logger = Logger.getLogger("DefaultExtensionManagerTest");
        // A failing extension is expected in several tests, and the stack trace would bury the
        // assertion failures.
        logger.setLevel(Level.OFF);
        manager = new DefaultExtensionManager(() -> null, logger);
    }

    /**
     * A test extension that records its lifecycle.
     */
    private final class Recording implements NpcExtension {

        private final String id;
        private final Set<String> dependencies;
        private final boolean failOnEnable;

        private Recording(String id, Set<String> dependencies, boolean failOnEnable) {
            this.id = id;
            this.dependencies = dependencies;
            this.failOnEnable = failOnEnable;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Set<String> dependencies() {
            return dependencies;
        }

        @Override
        public void onEnable(BetterNPCsApi api) {
            if (failOnEnable) {
                throw new IllegalStateException("deliberate failure in " + id);
            }
            events.add("enable:" + id);
        }

        @Override
        public void onDisable() {
            events.add("disable:" + id);
        }
    }

    private Recording extension(String id, String... dependencies) {
        return new Recording(id, Set.of(dependencies), false);
    }

    private Recording failing(String id) {
        return new Recording(id, Set.of(), true);
    }

    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("registration")
    class Registration {

        @Test
        void enablesAnExtensionWithNoDependencies() {
            manager.register(extension("shop"));

            assertThat(events).containsExactly("enable:shop");
            assertThat(manager.state("shop")).contains(ExtensionManager.State.ENABLED);
            assertThat(manager.isEnabled("shop")).isTrue();
        }

        @Test
        void refusesADuplicateId() {
            manager.register(extension("shop"));

            assertThatThrownBy(() -> manager.register(extension("shop")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void containsAFailingExtension() {
            manager.register(failing("broken"));
            manager.register(extension("healthy"));

            // An extension that cannot start is that extension's problem. Taking BetterNPCs down
            // with it would take every other extension too.
            assertThat(manager.state("broken")).contains(ExtensionManager.State.FAILED);
            assertThat(manager.isEnabled("healthy")).isTrue();
        }
    }

    @Nested
    @DisplayName("dependency ordering")
    class DependencyOrdering {

        @Test
        void waitsForADependencyThatIsNotRegisteredYet() {
            manager.register(extension("shop", "dialog"));

            // Plugins load in whatever order the server decides, so registering before a dependency
            // exists is normal, not an error.
            assertThat(manager.state("shop")).contains(ExtensionManager.State.PENDING);
            assertThat(events).isEmpty();
        }

        @Test
        void enablesWaitingExtensionsWhenTheirDependencyArrives() {
            manager.register(extension("shop", "dialog"));
            manager.register(extension("dialog"));

            assertThat(events).containsExactly("enable:dialog", "enable:shop");
        }

        @Test
        void resolvesAChainOfDependenciesRegisteredBackwards() {
            manager.register(extension("quests", "shop"));
            manager.register(extension("shop", "dialog"));
            manager.register(extension("dialog"));

            // Enabling one extension can satisfy another's dependencies, which can satisfy a third's.
            assertThat(events).containsExactly("enable:dialog", "enable:shop", "enable:quests");
        }

        @Test
        void doesNotEnableAnExtensionWhoseDependencyFailed() {
            manager.register(failing("dialog"));
            manager.register(extension("shop", "dialog"));

            assertThat(manager.state("shop")).contains(ExtensionManager.State.PENDING);
            assertThat(manager.isEnabled("shop")).isFalse();
        }

        @Test
        void reportsDependenciesNothingProvides() {
            manager.register(extension("shop", "dialog", "economy"));

            assertThat(manager.unsatisfiedDependencies()).containsExactlyInAnyOrder("dialog", "economy");
        }

        @Test
        void reportsNothingWhenEveryDependencyIsSatisfied() {
            manager.register(extension("dialog"));
            manager.register(extension("shop", "dialog"));

            assertThat(manager.unsatisfiedDependencies()).isEmpty();
        }
    }

    @Nested
    @DisplayName("unregistration")
    class Unregistration {

        @Test
        void disablesTheExtension() {
            manager.register(extension("shop"));
            events.clear();

            assertThat(manager.unregister("shop")).isTrue();

            assertThat(events).containsExactly("disable:shop");
            assertThat(manager.find("shop")).isEmpty();
        }

        @Test
        void disablesDependentsFirst() {
            manager.register(extension("dialog"));
            manager.register(extension("shop", "dialog"));
            events.clear();

            manager.unregister("dialog");

            // A dependent must still be able to use what it depends on while it cleans up.
            assertThat(events).containsExactly("disable:shop", "disable:dialog");
        }

        @Test
        void leavesDependentsPendingRatherThanRemovingThem() {
            manager.register(extension("dialog"));
            manager.register(extension("shop", "dialog"));

            manager.unregister("dialog");

            assertThat(manager.state("shop")).contains(ExtensionManager.State.PENDING);
            assertThat(manager.find("shop")).isPresent();
        }

        @Test
        void restoresTheChainWhenTheDependencyComesBack() {
            manager.register(extension("dialog"));
            manager.register(extension("shop", "dialog"));
            manager.unregister("dialog");
            events.clear();

            manager.register(extension("dialog"));

            assertThat(events).containsExactly("enable:dialog", "enable:shop");
            assertThat(manager.isEnabled("shop")).isTrue();
        }

        @Test
        void reportsRemovingSomethingThatIsNotRegistered() {
            assertThat(manager.unregister("nothing")).isFalse();
        }
    }

    @Test
    void disablesEverythingInReverseOrderOnShutdown() {
        manager.register(extension("dialog"));
        manager.register(extension("shop", "dialog"));
        manager.register(extension("quests", "shop"));
        events.clear();

        manager.disableAll();

        assertThat(events).containsExactly("disable:quests", "disable:shop", "disable:dialog");
    }

    @Test
    void listsEveryRegisteredExtensionWhateverItsState() {
        manager.register(extension("enabled"));
        manager.register(extension("pending", "missing"));
        manager.register(failing("broken"));

        assertThat(manager.extensions()).extracting(NpcExtension::id)
                .containsExactlyInAnyOrder("enabled", "pending", "broken");
    }
}
