import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar

plugins {
    id("com.gtnewhorizons.retrofuturagradle")
    id("xyz.wagyourtail.jvmdowngrader")
    id("compat.platform")
}

val modId = property("mod_id") as String
val coremodClass = "dev.example.compat.mc1122.CompatCoremod"
val refmap = "mixins.$modId.refmap.json"
val mappingChannel = property("mapping_channel_1122") as String
val mappingVersion = property("mapping_version_1122") as String
val shadePrefix = property("jvmdg_shade_path") as String

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
    mcpMappingChannel = mappingChannel
    mcpMappingVersion = mappingVersion
    username = "Developer"
    extraRunJvmArguments.addAll(
        "-Dfml.coreMods.load=$coremodClass",
        "-Dmixin.debug.export=true",
        "-Dmixin.checks.interfaces=true",
    )
}

repositories {
    maven("https://maven.cleanroommc.com") { name = "CleanroomMC" }
}

dependencies {
    val mixinSpec = modUtils.enableMixins(
        "zone.rong:mixinbooter:${property("mixinbooter_version")}", refmap,
    ) as String

    implementation(mixinSpec) {
        isTransitive = false
    }
    annotationProcessor("org.ow2.asm:asm-debug-all:5.2")
    annotationProcessor("com.google.guava:guava:32.1.2-jre")
    annotationProcessor("com.google.code.gson:gson:2.8.9")
    annotationProcessor(mixinSpec)
}

tasks.jar {
    manifest.attributes(
        "FMLCorePlugin" to coremodClass,
        "FMLCorePluginContainsFMLMod" to "true",
        "ForceLoadAsMod" to "true",
    )
}

// Two release jars: <name>.jar (downgraded to Java 8) and <name>-java21.jar (for Cleanroom and similar).
jvmdg.shadePath.set { shadePrefix }

tasks.named<DowngradeJar>("downgradeJar") {
    archiveClassifier = "dev-downgraded"
}

val shadeDowngradedApi = tasks.named<ShadeJar>("shadeDowngradedApi") {
    archiveClassifier = "dev-shaded"
}

val reobfJar = tasks.named<ReobfuscatedJar>("reobfJar") {
    archiveClassifier = "java21"
}

// Created by RFG's reobf<JarTask> rule.
val reobfDowngradedJar = tasks.named<ReobfuscatedJar>("reobfShadeDowngradedApi") {
    extraSrgFiles.from(reobfJar.map { it.extraSrgFiles })
    extraSrgEntries.addAll(reobfJar.flatMap { it.extraSrgEntries })
}

tasks.assemble {
    dependsOn(reobfDowngradedJar)
}

// Dev runs use Java 8, so swap the Java 21 dev jar and embedded core for the downgraded jar.
listOf("runClient", "runServer").forEach { runTask ->
    tasks.named<JavaExec>(runTask) {
        val original = (classpath as ConfigurableFileCollection).from.toList()
        val devJar = tasks.jar.flatMap { it.archiveFile }
        val embed = configurations["embed"]
        dependsOn(shadeDowngradedApi)
        classpath = files(shadeDowngradedApi) +
            files(original).filter { it != devJar.get().asFile && it !in embed }
    }
}
