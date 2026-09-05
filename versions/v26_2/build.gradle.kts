import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    id("betternpcs.nms-conventions")
}

base.archivesName = "betternpcs-v26_2"

// Minecraft 26.1+ ships Java 25 class files, so this module needs a JDK 25 toolchain — javac cannot
// read class files newer than itself. The foojay resolver provisions it automatically.
java {
    toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.javaToolchainModern.get().toInt())
}

// paper-api for 26.x publishes Gradle metadata declaring "requires JVM 25". Because this module
// still emits Java 21 bytecode (options.release, see betternpcs.java-conventions), Gradle would
// otherwise ask for a JVM-21-compatible variant and refuse the dependency outright.
//
// Only the *consumer* side is relaxed. The variant this module *publishes* keeps target 21, which
// is what lets :plugin — compiled at 21 — depend on this adapter at all. --release still guarantees
// no Java 22+ API can slip into the bytecode.
listOf(configurations.compileClasspath, configurations.runtimeClasspath,
       configurations.testCompileClasspath, configurations.testRuntimeClasspath).forEach { configuration ->
    configuration.configure {
        attributes {
            attribute(
                TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
                libs.versions.javaToolchainModern.get().toInt()
            )
        }
    }
}

dependencies {
    // The one fact that defines this module.
    paperweight.paperDevBundle("26.2.build.121-stable")
}
