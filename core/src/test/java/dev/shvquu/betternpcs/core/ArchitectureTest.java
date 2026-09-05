package dev.shvquu.betternpcs.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces the boundaries the architecture depends on.
 *
 * <p>These are the rules that stay true only if something checks them. Every one of them is easy to
 * break with a single import that compiles perfectly well and fails at runtime on a Minecraft
 * version nobody tested — which is exactly the failure this project is built to avoid.
 */
class ArchitectureTest {

    private static final JavaClasses PRODUCTION_CODE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.shvquu.betternpcs");

    @Test
    @DisplayName("the engine never imports Minecraft internals")
    void engineDoesNotTouchNms() {
        // The whole multi-version strategy rests on this. One net.minecraft import in core is a
        // class that links against one Minecraft version, in a module that is loaded on all of them.
        ArchRule rule = noClasses()
                .that().resideInAPackage("dev.shvquu.betternpcs.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "net.minecraft..",
                        "org.bukkit.craftbukkit..")
                .because("only the versions/v* adapters may touch Minecraft internals; everything "
                        + "else must go through VersionAdapter");

        rule.check(PRODUCTION_CODE);
    }

    @Test
    @DisplayName("the public API never imports Minecraft internals")
    void apiDoesNotTouchNms() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("dev.shvquu.betternpcs.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "net.minecraft..",
                        "org.bukkit.craftbukkit..")
                .because("a third-party plugin compiles against the API and must not be dragged "
                        + "into a particular Minecraft version by doing so");

        rule.check(PRODUCTION_CODE);
    }

    @Test
    @DisplayName("the public API does not depend on the engine")
    void apiDoesNotDependOnCore() {
        // The dependency runs one way. An API type that reaches into core would make the API
        // unusable without shipping the implementation alongside it.
        ArchRule rule = noClasses()
                .that().resideInAPackage("dev.shvquu.betternpcs.api..")
                .should().dependOnClassesThat().resideInAPackage("dev.shvquu.betternpcs.core..")
                .because("the API is published on its own and must compile without the engine");

        rule.check(PRODUCTION_CODE);
    }

    @Test
    @DisplayName("nothing reaches the server through Bukkit's static holder where it was handed one")
    void engineDoesNotUseBukkitSchedulerDirectly() {
        // The engine takes a Scheduler and an EventDispatcher precisely so that it can be tested
        // without a server. Calling Bukkit's own scheduler behind their backs would quietly undo
        // that and make the next engine test impossible to write.
        ArchRule rule = noClasses()
                .that().resideInAPackage("dev.shvquu.betternpcs.core.npc..")
                .should().dependOnClassesThat().haveFullyQualifiedName("org.bukkit.scheduler.BukkitScheduler")
                .because("engine timing goes through Scheduler, which tests can drive by hand");

        rule.check(PRODUCTION_CODE);
    }
}
