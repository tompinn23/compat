// Included from the root build's pluginManagement, so Compat Core and the example build with the plugin
// straight from source. Published alongside Compat Core for other mods.
rootProject.name = "compat-gradle"

dependencyResolutionManagement {
    repositories {
        // RetroFuturaGradle and its dependencies.
        maven("https://nexus.gtnewhorizons.com/repository/public/") {
            name = "GTNH"
            content { includeGroupByRegex("com\\.gtnewhorizons.*") }
        }
        // Architectury Loom and its dependencies.
        maven("https://maven.architectury.dev/") { name = "Architectury" }
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.minecraftforge.net/") { name = "Forge" }
        gradlePluginPortal()
        mavenCentral()
    }
}
