import org.gradle.api.publish.PublishingExtension

// The compat plugin brings RetroFuturaGradle, Architectury Loom and JvmDowngrader with it. Declaring it
// here, unapplied, puts one copy of all of them in the root classloader scope that every subproject
// shares. RFG needs that: applied to both :mc1710 and :mc1122 from separate scopes, it loads twice and
// its shared RfgCacheService cannot cross the two loaders. One scope also means one resolved classpath,
// so JvmDowngrader gets the newer commons-compress it needs rather than RFG's or Loom's.
plugins {
    id("org.yonside.compat") apply false
}

subprojects {
    // The example's version projects share names with Compat's stubs projects (:example:mc1710 and
    // :mc1710), and Gradle treats projects with the same group:name:version as one module: the example's
    // stubs dependency would resolve to the example project itself. A separate group keeps them apart.
    group = property("mod_group") as String + if (path.startsWith(":example")) ".example" else ""
    version = property("mod_version") as String

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}

val compatCore = listOf(":core", ":mc1710", ":mc1122", ":mc1165")
val stubProjects = mapOf("1.7.10" to ":mc1710", "1.12.2" to ":mc1122", "1.16.5" to ":mc1165")

// Mods link against org.yonside.compat:compatcore-<minecraft> from Maven; inside this build, use the stubs
// projects instead, the way an included build would.
subprojects {
    configurations.matching { it.name == "compatStubs" }.configureEach {
        resolutionStrategy.dependencySubstitution {
            stubProjects.forEach { (minecraft, path) ->
                substitute(module("org.yonside.compat:compatcore-$minecraft")).using(project(path))
            }
        }
    }
}

// Compat (the API and the stubs) is published to the yonside Maven repo; the example is never published.
compatCore.forEach { path ->
    project(path) {
        pluginManager.withPlugin("maven-publish") {
            extensions.configure<PublishingExtension> {
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
        }
    }
}

tasks.register<Copy>("dist") {
    group = "build"
    description = "Builds Compat (the API and every version's stubs) and collects the jars mods link against."
    dependsOn(compatCore.map { "$it:build" })
    into(layout.buildDirectory.dir("dist"))
    compatCore.forEach { path ->
        from(project(path).layout.buildDirectory.dir("libs")) {
            include("*.jar")
            exclude("*-sources.jar", "*-javadoc.jar", "*-srg.jar", "*-downgraded*.jar")
        }
    }
}

// The compat Gradle plugin (gradle-plugin/) is a plugin build, which Gradle keeps out of reach of this
// build's tasks, so it is tested and published on its own: ./gradlew -p gradle-plugin check / publish.
tasks.register("publishAll") {
    group = "publishing"
    description = "Publishes Compat (the API and every Minecraft version's stubs) to the yonside Maven repo."
    dependsOn(compatCore.map { "$it:publishCompatPublicationToYonsideRepository" })
}

tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Installs Compat into ~/.m2 for testing against a mod outside this build."
    dependsOn(compatCore.map { "$it:publishCompatPublicationToMavenLocal" })
}
