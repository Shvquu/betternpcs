pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    // Auto-provisions missing JDKs. Without this, building the 26.x adapters would require the
    // developer to install a JDK 25 by hand — the project must build from a fresh clone with no
    // local setup, so the resolver is not optional.
    // Version duplicated from gradle/libs.versions.toml (foojay): settings plugin blocks cannot
    // read the version catalogue.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "betternpcs"

// --- Public API (MIT) -----------------------------------------------------------------------------
include("api")

// --- Implementation (GPL-3.0) ---------------------------------------------------------------------
include("core")
include("storage:sql")
include("plugin")

// --- Version adapters -----------------------------------------------------------------------------
// One module per NMS-incompatible Minecraft version. `common` holds adapter helpers that need no NMS.
// Modules are merged if two versions turn out to be source-compatible for our packet surface.
include("versions:common")
include("versions:v1_21_4")
include("versions:v1_21_5")
include("versions:v1_21_8")
include("versions:v1_21_11")
include("versions:v26_1")
include("versions:v26_2")

// --- Examples -------------------------------------------------------------------------------------
// Part of the normal build on purpose: if the public API breaks, this module stops compiling.
include("examples:example-plugin")
