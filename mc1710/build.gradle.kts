// Compat's 1.7.10 stubs: the API (:core) implemented against Minecraft 1.7.10 and Forge. Not a mod. The
// compat plugin links mods against this jar, copying the stubs each mod uses into it.
plugins {
    id("org.yonside.compat")
}

compat {
    commonProject(":core")
    stubs("1.7.10")
}
