plugins {
    id("betternpcs.java-conventions")
}

base.archivesName = "betternpcs-versions-common"

// Helpers shared by the NMS adapters that do NOT themselves need NMS — packet-independent maths,
// entity id allocation, viewer bookkeeping. Keeping them here means the per-version modules contain
// only the code that genuinely differs between Minecraft versions.

dependencies {
    api(project(":api"))
    compileOnly(project(":core"))
    compileOnly(libs.paper.api)

    testImplementation(project(":core"))
    testImplementation(libs.paper.api)
}
