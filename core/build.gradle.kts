// Compat Core's common project: the API and framework every Minecraft version shares, Java 21 with no
// Minecraft or Forge. Embedded in each version's jar and published as compatcore-common, which mods
// built on Compat Core compile against.
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
