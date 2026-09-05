plugins {
    id("betternpcs.nms-conventions")
}

base.archivesName = "betternpcs-v1_21_5"

dependencies {
    // The one fact that defines this module.
    paperweight.paperDevBundle("1.21.5-R0.1-SNAPSHOT")
}
