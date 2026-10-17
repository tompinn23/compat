// Compat Core for Minecraft 1.12.2. The compat plugin applies RetroFuturaGradle, embeds :core, generates
// mcmod.info (CompatCoreMod is hand-written, so no @Mod class is generated) and builds the Java 8 and
// Java 21 release jars.
plugins {
    id("org.yonside.compat")
}

compat {
    commonProject(":core")
    minecraft("1.12.2")
    mixins()
    coremod("org.yonside.compat.mc1122.CompatCoremod")
    // Dev runs boot with the example installed, the way a player's game would.
    devRunMod(":example:mc1122")
}
