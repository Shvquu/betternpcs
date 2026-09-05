import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-library`
}

// Precompiled script plugins cannot use the type-safe `libs.` accessors, so the catalogue is read
// through its API instead. Values still come from gradle/libs.versions.toml — no duplication.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun catalogVersion(alias: String): String = catalog.findVersion(alias)
    .orElseThrow { IllegalStateException("Version '$alias' missing from gradle/libs.versions.toml") }
    .requiredVersion

fun catalogLibrary(alias: String) = catalog.findLibrary(alias)
    .orElseThrow { IllegalStateException("Library '$alias' missing from gradle/libs.versions.toml") }

java {
    toolchain.languageVersion = JavaLanguageVersion.of(catalogVersion("javaToolchainDefault").toInt())
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    // --release (not just -target) so that a JDK 25 toolchain cannot accidentally let a Java 22+
    // API slip into bytecode that has to run on the Java 21 JVM of a 1.21.x server.
    options.release = catalogVersion("javaTarget").toInt()
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-parameters",
            "-Xlint:all,-serial,-processing,-this-escape",
        )
    )
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"

        // Deliberately NO `links("https://docs.oracle.com/.../api/")`. javadoc downloads the target's
        // element-list while it runs and fails the task outright if it cannot — behind a proxy, in an
        // offline CI runner, or on a machine with a TLS-intercepting antivirus, that turns
        // `./gradlew build` from a fresh clone into an error about SSL rather than about this code.
        // Hyperlinks into the JDK docs are not worth making the build depend on the network; the
        // doclint checks below are the part that actually protects the API.
        addBooleanOption("Xdoclint:all", true)

        // Reproducible output: without this every javadoc HTML file carries a build timestamp, so
        // two builds of the same commit produce different artifacts.
        noTimestamp(true)
    }
}

dependencies {
    testImplementation(platform(catalogLibrary("junit-bom")))
    testImplementation(catalogLibrary("junit-jupiter"))
    testRuntimeOnly(catalogLibrary("junit-platform-launcher"))
    testImplementation(catalogLibrary("assertj"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
        showStackTraces = true
    }
}
