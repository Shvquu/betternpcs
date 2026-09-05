plugins {
    id("betternpcs.java-conventions")
}

base.archivesName = "betternpcs-storage-sql"

dependencies {
    api(project(":core"))
    compileOnly(libs.paper.api)

    // Not shaded. Paper's plugin loader downloads these at runtime (see BetterNPCsPluginLoader in
    // :plugin) — relocating org.sqlite would break its JNI bindings, which resolve native methods
    // by fully qualified class name.
    compileOnly(libs.hikari)
    compileOnly(libs.sqlite.jdbc)

    // Integration tests run against a real SQLite file in a temporary folder — no mocking of JDBC,
    // no external service. That is the only way to find out whether the SQL is actually valid.
    testImplementation(libs.hikari)
    testImplementation(libs.sqlite.jdbc)

    // Equipment serialisation goes through ItemStack.serializeAsBytes, which needs a server. See the
    // note on `paperApiTest` in the version catalogue for why the tests use a newer API than the
    // code is compiled against.
    testImplementation(libs.paper.api.test)
    testImplementation(libs.mockbukkit)
}
