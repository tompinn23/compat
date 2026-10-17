// The example for Minecraft 1.12.2: no code of its own. The compat plugin embeds example/common, generates
// the @Mod class and metadata, and builds compatexample-1.12.2-<version>.jar.
plugins {
    id("org.yonside.compat")
}

compat {
    minecraft("1.12.2")
}
