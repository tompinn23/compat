pluginManagement {
    // org.yonside.compat, built from source.
    includeBuild("gradle-plugin")

    // For the compat plugin's own dependencies: RetroFuturaGradle, Architectury Loom and theirs.
    repositories {
        maven("https://nexus.gtnewhorizons.com/repository/public/") {
            name = "GTNH"
            content { includeGroupByRegex("com\\.gtnewhorizons.*") }
        }
        maven("https://maven.architectury.dev/") { name = "Architectury" }
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.minecraftforge.net/") { name = "Forge" }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "compat-framework"

include(":core")
include(":mc1710")
include(":mc1122")
include(":mc1165")

// The example mod: all its code in example/common, one build-script-only project per version.
include(":example:common")
include(":example:mc1710")
include(":example:mc1122")
include(":example:mc1165")
