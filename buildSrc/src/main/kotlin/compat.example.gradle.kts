// Shared setup for the example mods. They are deliberately plain Java 8 mods with no mixins and no
// JvmDowngrader: that is what a third-party mod building on Compat Core actually looks like, and it
// keeps the dev runs on one Java version end to end.

plugins {
    java
}

// example:common is shaded in, the same way Compat Core shades in :core.
val embed = configurations.create("embed") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

// Compat Core's dev mod jar: named mappings, Java 8 bytecode, and the downgraded :core API inside it.
// Never shaded in -- at runtime it is a separate mod the player installs, and the injected
// CompatWorld interface only works if exactly one copy of it is on the classpath.
val compatMod = configurations.create("compatMod") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

configurations.implementation {
    extendsFrom(embed, compatMod)
}

dependencies {
    // By configuration name, so Gradle's JVM-version matching doesn't reject it on a Java 8 project.
    embed(project(path = ":example:common", configuration = "embedElements"))
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
        "mod_id" to project.property("example_mod_id").toString(),
        "mod_name" to project.property("example_mod_name").toString(),
        "mod_description" to project.property("example_mod_description").toString(),
        "mod_license" to project.property("mod_license").toString(),
        "compat_mod_id" to project.property("mod_id").toString(),
    )
    inputs.properties(props)
    filesMatching(listOf("mcmod.info", "META-INF/mods.toml")) {
        expand(props)
    }
}
