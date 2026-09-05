plugins {
    id("betternpcs.java-conventions")
}

base.archivesName = "betternpcs-core"

dependencies {
    api(project(":api"))
    compileOnly(libs.paper.api)

    // Enforces that NMS never leaks out of versions/v*.
    testImplementation(libs.archunit.junit5)

    // A stand-in server, so the engine's visibility and tracking logic can be tested against real
    // Player and World objects rather than against hand-written stubs that would drift from the
    // interfaces they imitate.
    //
    // MockBukkit ships one build per Minecraft version and refuses to start against a different
    // one, so the tests use `paper-api-test` rather than the 1.21.4 baseline the code is compiled
    // against. That is the harmless direction — see the note on `paperApiTest` in the catalogue.
    testImplementation(libs.paper.api.test)
    testImplementation(libs.mockbukkit)
}
