import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar

plugins {
    `java-library`
    id("xyz.wagyourtail.jvmdowngrader")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
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

val embedElements = configurations.create("embedElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(embedElements.name, tasks.jar)
}

// A Java 8 view of the API. Java 21 class files are unreadable to a Java 8 javac, which is what most
// 1.7.10 and 1.12.2 mods still use, so consumers -- including the example mods here -- compile
// against this instead of the Java 21 jar. Compile-only: the real classes ship inside each mod jar.
val api8Jar = tasks.named<DowngradeJar>("downgradeJar") {
    archiveClassifier = "java8"
}

val api8Elements = configurations.create("api8Elements") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(api8Elements.name, api8Jar)
}
