// Compat Core for Minecraft 1.7.10. The compat plugin applies RetroFuturaGradle, embeds :core, generates
// mcmod.info (CompatCoreMod is hand-written, so no @Mod class is generated) and builds the Java 8 and
// Java 21 release jars.
plugins {
    id("org.yonside.compat")
}

compat {
    commonProject(":core")
    minecraft("1.7.10")
    mixins()
    coremod("org.yonside.compat.mc1710.CompatCoremod")
    // runClient21 / runServer21: Java 21 with lwjgl3ify, running the -java21 code without downgrading.
    lwjgl3ify()
    // Dev runs boot with the example installed, the way a player's game would.
    devRunMod(":example:mc1710")
}
