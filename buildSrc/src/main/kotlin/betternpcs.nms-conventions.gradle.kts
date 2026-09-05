plugins {
    id("betternpcs.java-conventions")
    id("io.papermc.paperweight.userdev")
}

// The version adapters are the ONLY modules permitted to import net.minecraft.* or
// org.bukkit.craftbukkit.*. An ArchUnit test in :core enforces that for everyone else.
//
// paperweight-userdev is left at its default artifact configuration, which produces Mojang-mapped
// output. That is correct for every version we support: Paper has shipped a Mojang-mapped runtime
// since 1.20.5, and Minecraft 26.1+ removed obfuscation entirely, so there is nothing to reobfuscate
// back to. The final shaded jar must still declare the mappings namespace in its manifest — see the
// :plugin module — because Paper otherwise assumes a plugin is Spigot-mapped.
//
// Each module declares its own `paperweight.paperDevBundle(...)`: that coordinate is the single fact
// that defines what the module is, so it belongs next to the module, not in a shared file.

dependencies {
    compileOnly(project(":api"))
    compileOnly(project(":core"))
    compileOnly(project(":versions:common"))
}
