// Apply after RFG / Architectury Loom. Each module also applies JvmDowngrader itself (see settings.gradle.kts).

plugins {
    java
}

val embed = configurations.create("embed") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

configurations.implementation {
    extendsFrom(embed)
}

dependencies {
    // By configuration name, so Gradle's JVM-version matching doesn't reject core on a Java 8 project.
    embed(project(path = ":core", configuration = "embedElements"))
}

// The java toolchain stays at 8 for the Minecraft tooling and dev runs; only our sources compile at 21.
tasks.compileJava {
    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(21)
    }
    options.release = 21
}

tasks.jar {
    dependsOn(embed)
    from(embed.elements.map { jars -> jars.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "mod_id" to project.property("mod_id").toString(),
        "mod_name" to project.property("mod_name").toString(),
    )
    inputs.properties(props)
    filesMatching(listOf("mcmod.info", "META-INF/mods.toml")) {
        expand(props)
    }
}
