pluginManagement {
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

    // Plugins are applied per subproject so RFG and Loom get separate classloaders.
    // Never apply them (or JvmDowngrader) in the root project or add them to buildSrc.
    plugins {
        id("com.gtnewhorizons.retrofuturagradle") version "2.0.6"
        id("dev.architectury.loom") version "1.17-SNAPSHOT"
        id("xyz.wagyourtail.jvmdowngrader") version "1.3.6"
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

include(":example:common")
include(":example:mc1710")
include(":example:mc1122")
include(":example:mc1165")
