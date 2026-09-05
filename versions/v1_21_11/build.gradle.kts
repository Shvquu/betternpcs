plugins {
    id("betternpcs.nms-conventions")
}

base.archivesName = "betternpcs-v1_21_11"

dependencies {
    // The one fact that defines this module.
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
}
