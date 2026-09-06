plugins {
    id("betternpcs.java-conventions")
    `maven-publish`
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

// Only this module is published. core, storage and the version adapters are implementation detail
// and are not something a third-party plugin should be able to compile against by accident.
publishing {
    publications {
        create<MavenPublication>("api") {
            // Set explicitly: the publication would otherwise take the Gradle project name, which is
            // just "api", giving the coordinate dev.shvquu.betternpcs:api while the jar on disk is
            // called betternpcs-api. Two names for one artifact is a support question waiting to
            // happen.
            artifactId = base.archivesName.get()

            // Carries the sources and javadoc jars too, because withSourcesJar() and
            // withJavadocJar() above register them with the java component.
            from(components["java"])

            pom {
                name = "BetterNPCs API"
                description = "The public API of BetterNPCs, a modern NPC plugin for Paper."
                url = "https://github.com/Shvquu/betternpcs"

                licenses {
                    license {
                        // The API alone is MIT. The implementation is GPL-3.0, which is why they are
                        // separate modules: a plugin that compiles against this is not bound by it.
                        name = "MIT License"
                        url = "https://github.com/Shvquu/betternpcs/blob/main/api/LICENSE"
                    }
                }
                developers {
                    developer {
                        id = "shvquu"
                        name = "shvquu"
                    }
                }
                scm {
                    url = "https://github.com/Shvquu/betternpcs"
                    connection = "scm:git:https://github.com/Shvquu/betternpcs.git"
                    developerConnection = "scm:git:ssh://git@github.com/Shvquu/betternpcs.git"
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Shvquu/betternpcs")
            credentials {
                // Supplied by the release workflow from the built-in GITHUB_TOKEN. Falling back to
                // Gradle properties lets a maintainer publish by hand without editing this file.
                username = providers.environmentVariable("GITHUB_ACTOR")
                    .orElse(providers.gradleProperty("gpr.user"))
                    .orNull
                password = providers.environmentVariable("GITHUB_TOKEN")
                    .orElse(providers.gradleProperty("gpr.token"))
                    .orNull
            }
        }
    }
}
