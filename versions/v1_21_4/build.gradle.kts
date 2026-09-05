plugins {
    id("betternpcs.nms-conventions")
}

base.archivesName = "betternpcs-v1_21_4"

dependencies {
    // The one fact that defines this module.
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
}
