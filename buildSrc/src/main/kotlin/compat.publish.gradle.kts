// Maven publishing for the per-Minecraft-version mod jars. Applied alongside compat.platform.
// Core Gradle plugins only -- RFG, Loom and JvmDowngrader must stay out of buildSrc (see settings.gradle.kts).

plugins {
    java
    `maven-publish`
}

// Consumers compile against the dev jar, so ship sources beside it.
java {
    withSourcesJar()
}

publishing {
    publications {
        // Artifacts only, deliberately no `from(components["java"])`: that would write Minecraft, Forge
        // and the mixin provider into the POM as transitive dependencies. Those are mods a player
        // installs, not jars Gradle can resolve, and core is already embedded in every jar we publish.
        create<MavenPublication>("mod") {
            pom {
                description = project.property("mod_description").toString()
                licenses {
                    license {
                        name = project.property("mod_license").toString()
                    }
                }
                // TODO: add url { }, scm { } and developers { } once the repo is public.
            }
        }
    }

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

// archivesName is set by each module's build script, so it is only readable once that has evaluated.
afterEvaluate {
    val archives = base.archivesName.get()
    val mcVersion = archives.removePrefix("${project.property("mod_id")}-")
    publishing.publications.named<MavenPublication>("mod") {
        // One artifactId per Minecraft version: org.yonside.compat:compatcore-1.7.10:<version>
        artifactId = archives
        pom.name = "${project.property("mod_name")} for Minecraft $mcVersion"
    }
}
