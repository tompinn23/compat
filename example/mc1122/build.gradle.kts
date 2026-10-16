plugins {
    id("com.gtnewhorizons.retrofuturagradle")
    id("compat.example")
}

val modId = property("example_mod_id") as String
val refmap = "mixins.$modId.refmap.json"
// Manifest coremod discovery does not happen for classpath entries in a dev run, so the run has to
// name Compat Core's coremod the same way Compat Core's own runClient does.
val compatCoremod = "org.yonside.compat.mc1122.CompatCoremod"

base {
    archivesName = "$modId-1.12.2"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
        vendor = JvmVendorSpec.AZUL
    }
}

minecraft {
    mcVersion = "1.12.2"
    mcpMappingChannel = property("mapping_channel_1122") as String
    mcpMappingVersion = property("mapping_version_1122") as String
    username = "Developer"
    extraRunJvmArguments.addAll(
        "-Dfml.coreMods.load=$compatCoremod",
        "-Dmixin.debug.export=true",
    )
}

repositories {
    maven("https://maven.cleanroommc.com") { name = "CleanroomMC" }
}

dependencies {
    "compatMod"(project(path = ":mc1122", configuration = "devModElements"))

    // The example has no mixins of its own, but Compat Core's do not apply without the mixin
    // bootstrap mod in the run, and enableMixins is what sets that up for a dev launch.
    val mixinSpec = modUtils.enableMixins(
        "zone.rong:mixinbooter:${property("mixinbooter_version")}", refmap,
    ) as String

    implementation(mixinSpec) {
        isTransitive = false
    }
}
