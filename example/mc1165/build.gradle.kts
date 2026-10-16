// Loom runs in Forge mode because example/mc1165/gradle.properties sets loom.platform=forge.
plugins {
    id("dev.architectury.loom")
    id("compat.example")
}

val modId = property("example_mod_id") as String
val compatModId = property("mod_id") as String
val forgeVersion = property("forge_1165") as String

base {
    archivesName = "$modId-1.16.5"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}

loom {
    silentMojangMappingsLicense()

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
            configuration(configurations["embed"])
        }
        // Compat Core as a separate mod in the dev run, matching how a player installs it. Its jar
        // carries its own MixinConfigs manifest entry, so Forge picks the mixins up from there --
        // 1.16.5 needs no coremod and no -Dfml.coreMods.load.
        create(compatModId) {
            configuration(configurations["compatMod"])
        }
    }

    runs {
        configureEach {
            jvmArguments.add("-Dmixin.debug.export=true")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:1.16.5")
    mappings(loom.officialMojangMappings())
    "forge"("net.minecraftforge:forge:$forgeVersion")

    "compatMod"(project(path = ":mc1165", configuration = "devModElements"))
}
