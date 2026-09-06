plugins {
    id("betternpcs.java-conventions")
}

base.archivesName = "betternpcs-storage-mongodb"

dependencies {
    api(project(":core"))
    compileOnly(libs.paper.api)

    // Not shaded. Paper's plugin loader downloads it at runtime, and only when config.yml actually
    // names MONGODB — the driver is several megabytes and the overwhelming majority of servers run
    // on SQLite. See BetterNpcsPluginLoader in :plugin.
    compileOnly(libs.mongodb.driver)

    // Integration tests run against a real mongod that flapdoodle downloads and starts, in the same
    // spirit as the SQLite tests: a mocked driver would only prove that the code calls the methods
    // it was written to call.
    testImplementation(libs.mongodb.driver)
    testImplementation(libs.embedded.mongo)

    // Equipment serialisation goes through ItemStack.serializeAsBytes, which needs a server. See the
    // note on `paperApiTest` in the version catalogue for why the tests use a newer API than the
    // code is compiled against.
    testImplementation(libs.paper.api.test)
    testImplementation(libs.mockbukkit)
}
