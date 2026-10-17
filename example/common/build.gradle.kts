// The example mod's entire code: Java 21, no Minecraft or Forge imports. ExampleMod is annotated
// @CompatMod, so each version project (example/mc1710 and so on, build scripts only) gets a generated
// @Mod class that drives it, plus generated mcmod.info / mods.toml, and is linked against that
// version's Compat stubs. Not published.
plugins {
    id("org.yonside.compat")
}

compat {
    common()
    mod {
        modId = "compatexample"
        displayName = "Compat Example"
        description = "Example mod built with Compat"
        license = property("mod_license") as String
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Compat's API, also on every version project's classpath through compileOnlyApi. Linking replaces
    // it, so nothing of it is needed at runtime. A mod outside this build would use
    // org.yonside.compat:compatcore-common.
    // By configuration name, like the version projects' own dependency on their common project.
    compileOnlyApi(project(path = ":core", configuration = "compatCommon"))
    // 1.7.10's log4j, the oldest of the three, so only API every version has gets used.
    compileOnly("org.apache.logging.log4j:log4j-api:2.0-beta9")
}
