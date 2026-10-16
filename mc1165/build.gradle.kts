import net.fabricmc.loom.task.RemapJarTask
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar

// Loom runs in Forge mode because mc1165/gradle.properties sets loom.platform=forge.
plugins {
    id("dev.architectury.loom")
    id("xyz.wagyourtail.jvmdowngrader")
    id("compat.platform")
    id("compat.publish")
}

val modId = property("mod_id") as String
val modName = property("mod_name") as String
val forgeVersion = property("forge_1165") as String
val mixinConfig = "mixins.$modId.json"
val refmap = "mixins.$modId.refmap.json"
val shadePrefix = property("jvmdg_shade_path") as String

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

    forge {
        mixinConfig(mixinConfig)
    }

    mixin {
        defaultRefmapName = refmap
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
            // Same classloader for the mod and embedded core in dev, as in the shipped jar.
            configuration(configurations["embed"])
        }
    }

    runs {
        configureEach {
            jvmArguments.addAll("-Dmixin.debug.export=true", "-Dmixin.checks.interfaces=true")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:1.16.5")
    mappings(loom.officialMojangMappings())
    "forge"("net.minecraftforge:forge:$forgeVersion")
}

tasks.jar {
    archiveClassifier = "dev"
    manifest.attributes(
        "Specification-Title" to modId,
        "Specification-Version" to "1",
        "Implementation-Title" to modName,
        "Implementation-Version" to project.version,
        "MixinConfigs" to mixinConfig,
    )
}

// Two release jars: <name>.jar (downgraded to Java 8) and <name>-java21.jar.
jvmdg.shadePath.set { shadePrefix }

tasks.named<DowngradeJar>("downgradeJar") {
    archiveClassifier = "dev-downgraded"
}

val shadeDowngradedApi = tasks.named<ShadeJar>("shadeDowngradedApi") {
    archiveClassifier = "dev-shaded"
}

artifacts {
    add("devModElements", shadeDowngradedApi)
}

tasks.remapJar {
    archiveClassifier = ""
    dependsOn(shadeDowngradedApi)
    inputFile = shadeDowngradedApi.flatMap { it.archiveFile }
}

val remapJava21Jar = tasks.register<RemapJarTask>("remapJava21Jar") {
    dependsOn(tasks.jar)
    inputFile = tasks.jar.flatMap { it.archiveFile }
    archiveClassifier = "java21"
}

tasks.assemble {
    dependsOn(remapJava21Jar)
}

publishing {
    publications {
        named<MavenPublication>("mod") {
            artifact(tasks.remapJar)                  // release jar, downgraded to Java 8
            artifact(remapJava21Jar)                  // -java21
            artifact(tasks.jar)                       // -dev, what consumers compile against
            artifact(tasks.named<Jar>("sourcesJar"))  // -sources
        }
    }
}
