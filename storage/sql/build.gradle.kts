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

    testImplementation(libs.paper.api)
    testImplementation(libs.hikari)
    testRuntimeOnly(libs.sqlite.jdbc)
}
