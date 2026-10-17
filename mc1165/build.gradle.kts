// Compat Core for Minecraft 1.16.5. The compat plugin applies Architectury Loom in Forge mode, embeds
// :core, generates mods.toml and pack.mcmeta (CompatCoreMod is hand-written, so no @Mod class is
// generated) and builds the Java 8 and Java 21 release jars.
plugins {
    id("org.yonside.compat")
}

compat {
    commonProject(":core")
    minecraft("1.16.5")
    mixins()
    // Dev runs boot with the example installed, the way a player's game would.
    devRunMod(":example:mc1165")
}
