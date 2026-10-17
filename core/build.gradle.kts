// Compat's API: what mods compile against, Java 21 with no Minecraft or Forge. Each version's stubs
// project implements it and embeds it; linking replaces it in every mod, so it never ships. Published
// as compatcore-common.
plugins {
    id("org.yonside.compat")
}

compat {
    common()
    mod {
        modId = property("mod_id") as String  // frozen: published worlds and mods refer to it
        displayName = property("mod_name") as String
        description = property("mod_description") as String
        license = property("mod_license") as String
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
