import java.util.Properties

// org.yonside.compat: one plugin for Compat and every mod built with it. It applies RetroFuturaGradle,
// Architectury Loom and JvmDowngrader itself, so they are its dependencies; builds using it must not
// declare other versions of them. RFG is compiled for Java 25, so this build and its users run Gradle on
// Java 25; the plugin itself targets 21.
plugins {
    `java-gradle-plugin`
    `maven-publish`
}

// One version for the whole framework, read from the root build's gradle.properties.
val rootProperties = Properties().apply {
    file("../gradle.properties").reader().use { load(it) }
}

group = rootProperties.getProperty("mod_group")
version = rootProperties.getProperty("mod_version")

dependencies {
    implementation("org.ow2.asm:asm:9.9")
    implementation("org.ow2.asm:asm-commons:9.9")
    // Through their plugin markers, which resolve to the plugin jars and their dependencies.
    implementation("com.gtnewhorizons.retrofuturagradle:com.gtnewhorizons.retrofuturagradle.gradle.plugin:2.0.6")
    implementation("dev.architectury.loom:dev.architectury.loom.gradle.plugin:1.17-SNAPSHOT")
    implementation("xyz.wagyourtail.jvmdowngrader:gradle-plugin:1.3.6")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.compileJava {
    options.release = 21
    options.encoding = "UTF-8"
}

tasks.compileTestJava {
    options.release = 21
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}


gradlePlugin {
    plugins {
        create("compat") {
            id = "org.yonside.compat"
            implementationClass = "org.yonside.compat.gradle.CompatPlugin"
            displayName = "Compat"
            description = "Builds mods with Compat: a Java 21 common project plus one project per" +
                " Minecraft version, set up with RetroFuturaGradle or Architectury Loom, generating the @Mod class" +
                " and mcmod.info / mods.toml unless hand-written, and downgrading the release jars to Java 8."
        }
    }
}

publishing {
    repositories {
        maven {
            name = "yonside"
            val host = "https://maven.yonside.org"
            url = uri("$host/" + if (version.toString().endsWith("SNAPSHOT")) "snapshots" else "releases")
            credentials {
                username = providers.gradleProperty("yonsideUsername")
                    .orElse(providers.environmentVariable("YONSIDE_USERNAME")).orNull
                password = providers.gradleProperty("yonsidePassword")
                    .orElse(providers.environmentVariable("YONSIDE_PASSWORD")).orNull
            }
        }
    }
}

// Read by CompatPlugin.version(): mods link against the stubs of the plugin's own version by default.
tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("**/compat-plugin.properties") {
        expand(props)
    }
}
