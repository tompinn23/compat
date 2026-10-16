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
