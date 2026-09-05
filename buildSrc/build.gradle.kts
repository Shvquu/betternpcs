plugins {
    `kotlin-dsl`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    // Makes `io.papermc.paperweight.userdev` applicable from a precompiled script plugin.
    implementation(libs.paperweight.plugin)
}
