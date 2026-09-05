plugins {
    id("betternpcs.java-conventions")
}

base.archivesName = "betternpcs-example"

// Deliberately part of the normal build: this module compiles against the public API exactly the way
// a third-party developer would. If a change breaks API compatibility, `./gradlew build` fails here
// instead of in someone else's project.

dependencies {
    compileOnly(project(":api"))
    compileOnly(libs.paper.api)
}

// Keeps plugin.yml's version in step with the project version instead of relying on someone
// remembering to bump it.
tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}
