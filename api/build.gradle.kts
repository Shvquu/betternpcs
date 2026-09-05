plugins {
    id("betternpcs.java-conventions")
}

base.archivesName = "betternpcs-api"

java {
    // Published for third-party developers, so javadoc ships alongside sources.
    withJavadocJar()
}

dependencies {
    // Provided by the server at runtime. Compiled against the OLDEST supported Paper API so that
    // using something unavailable on 1.21.4 is a compile error rather than a user's crash report.
    compileOnly(libs.paper.api)

    // The API's own tests exercise value objects that reference Bukkit types (EntityType,
    // EquipmentSlot). They deliberately never start a server: everything tested here is pure logic,
    // which is what keeps the API testable at all.
    testImplementation(libs.paper.api)
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        title = "BetterNPCs API ${project.version}"
        windowTitle = "BetterNPCs API"
    }
    // Public API: an undocumented member is a bug, not a warning to scroll past. Xdoclint:all is
    // switched on for every module in betternpcs.java-conventions; here it is also made fatal, so a
    // missing @param on an API method fails the build rather than scrolling past in the log.
    isFailOnError = true
}
