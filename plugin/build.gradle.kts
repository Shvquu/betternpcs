plugins {
    id("betternpcs.java-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

base.archivesName = "BetterNPCs"

dependencies {
    implementation(project(":core"))
    implementation(project(":storage:sql"))
    implementation(project(":versions:common"))

    // Every adapter ships in the jar; exactly one is class-loaded at runtime, chosen by
    // VersionAdapterResolver. Loading is lazy, which is what allows adapters compiled against
    // different Minecraft versions to coexist in a single artifact.
    implementation(project(":versions:v1_21_4"))
    implementation(project(":versions:v1_21_5"))
    implementation(project(":versions:v1_21_8"))
    implementation(project(":versions:v1_21_11"))
    implementation(project(":versions:v26_1"))
    implementation(project(":versions:v26_2"))

    compileOnly(libs.paper.api)

    // The tests here check the *packaged* resources — config.yml and the language files — against
    // the code that reads them. That can only be done from this module, because this is where those
    // resources live.
    testImplementation(libs.paper.api)
}

// paper-plugin.yml and the runtime library coordinates both need versions that live in the version
// catalogue. Expanding them here keeps the catalogue the only place a version is written down.
tasks.processResources {
    val tokens = mapOf(
        "version" to project.version.toString(),
        "hikariVersion" to libs.versions.hikari.get(),
        "sqliteVersion" to libs.versions.sqlite.get(),
        "mysqlVersion" to libs.versions.mysqlDriver.get(),
        "mariadbVersion" to libs.versions.mariadbDriver.get(),
        "postgresqlVersion" to libs.versions.postgresqlDriver.get(),
    )
    inputs.properties(tokens)
    filesMatching(listOf("paper-plugin.yml", "betternpcs-libraries.properties")) {
        expand(tokens)
    }
}

tasks.shadowJar {
    archiveClassifier = ""

    // Without this Paper assumes a plugin is Spigot-mapped and tries to remap it on load, which
    // fails against our Mojang-mapped adapter classes. paperweight sets this on the per-module jars,
    // but the shaded artifact is assembled here, so it has to be set here too.
    manifest {
        attributes("paperweight-mappings-namespace" to "mojang")
    }

    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.runServer {
    // The lower end of the supported range. Run the upper end (26.2) by overriding this on the
    // command line: ./gradlew runServer -Pruntime.mc=26.2
    minecraftVersion(providers.gradleProperty("runtime.mc").getOrElse("1.21.4"))

    // Escape hatch for the test server's JVM. Needed on machines behind a TLS-intercepting proxy or
    // antivirus, where Paperclip cannot validate Mojang's certificate and the server never starts:
    //
    //   ./gradlew runServer "-Pruntime.jvmArgs=-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"
    //
    // Deliberately opt-in. Silently changing which certificates a JVM trusts is not something a
    // build script should do on anyone's behalf.
    providers.gradleProperty("runtime.jvmArgs").orNull
        ?.split(" ")
        ?.filter { it.isNotBlank() }
        ?.let { jvmArgs(it) }

    // The developer accepts Mojang's EULA themselves by creating plugin/run/eula.txt. Doing it for
    // them from a build script would be agreeing to a licence on someone else's behalf.
    doFirst {
        val eula = layout.projectDirectory.file("run/eula.txt").asFile
        if (!eula.exists()) {
            throw GradleException(
                "Create ${eula.path} containing 'eula=true' to accept Mojang's EULA " +
                    "(https://aka.ms/MinecraftEULA) before running a test server."
            )
        }
    }
}
