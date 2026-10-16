plugins {
    id("com.gtnewhorizons.retrofuturagradle")
    id("compat.example")
}

val modId = property("example_mod_id") as String
val refmap = "mixins.$modId.refmap.json"
// Manifest coremod discovery does not happen for classpath entries in a dev run, so the run has to
// name Compat Core's coremod the same way Compat Core's own runClient does.
val compatCoremod = "org.yonside.compat.mc1710.CompatCoremod"

base {
    archivesName = "$modId-1.7.10"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
        vendor = JvmVendorSpec.AZUL
    }
}

minecraft {
    mcVersion = "1.7.10"
    username = "Developer"
    extraRunJvmArguments.addAll(
        "-Dfml.coreMods.load=$compatCoremod",
        "-Dmixin.debug.export=true",
    )
}

repositories {
    maven("https://nexus.gtnewhorizons.com/repository/public/") { name = "GTNH" }
}

dependencies {
    "compatMod"(project(path = ":mc1710", configuration = "devModElements"))

    // The example has no mixins of its own, but Compat Core's do not apply without the mixin
    // bootstrap mod in the run, and enableMixins is what sets that up for a dev launch.
    val mixinSpec = "io.github.legacymoddingmc:unimixins:${property("unimixins_version")}:dev"
    implementation(modUtils.enableMixins(mixinSpec, refmap))
}
