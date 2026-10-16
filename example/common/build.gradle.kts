plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // The Java 8 view of the API. Compat Core supplies the real classes at runtime, so compile-only.
    compileOnly(project(path = ":core", configuration = "api8Elements"))
}

// Java 8 bytecode and Java 8 APIs only: the platform modules that shade this in run on a Java 8
// toolchain, and unlike Compat Core the example does not run JvmDowngrader.
tasks.compileJava {
    options.release = 8
}

val embedElements = configurations.create("embedElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(embedElements.name, tasks.jar)
}
