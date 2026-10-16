// RFG and Loom are declared but never applied here, so every subproject that applies one resolves
// it from a single root classloader scope. Without this, applying RFG to both :mc1122 and
// :example:mc1122 loads it twice and its shared RfgCacheService fails to cross the two loaders.
plugins {
    id("com.gtnewhorizons.retrofuturagradle") apply false
    id("dev.architectury.loom") apply false
}

subprojects {
    group = property("mod_group") as String
    version = property("mod_version") as String

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}

val mcModules = listOf("mc1710", "mc1122", "mc1165")

tasks.register<Copy>("dist") {
    group = "build"
    description = "Builds every Minecraft version and collects the release jars."
    dependsOn(mcModules.map { ":$it:build" })
    into(layout.buildDirectory.dir("dist"))
    mcModules.forEach { name ->
        from(project(":$name").layout.buildDirectory.dir("libs")) {
            include("*.jar")
            exclude("*-dev.jar", "*-dev-*.jar", "*-sources.jar", "*-javadoc.jar")
        }
    }
}

tasks.register("publishAll") {
    group = "publishing"
    description = "Publishes every Minecraft version's jars to the yonside Maven repo."
    dependsOn(mcModules.map { ":$it:publishModPublicationToYonsideRepository" })
}

tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Installs every Minecraft version's jars into ~/.m2 for testing against a consumer mod."
    dependsOn(mcModules.map { ":$it:publishModPublicationToMavenLocal" })
}
