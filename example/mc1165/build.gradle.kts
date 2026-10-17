// The example for Minecraft 1.16.5: no code of its own. The compat plugin embeds example/common, generates
// the @Mod class and metadata, links everything against Compat's 1.16.5 stubs (copying the ones it uses
// into the jar) and builds compatexample-1.16.5-<version>.jar, which needs nothing else installed.
// runServer / runClient run it on its own.
plugins {
    id("org.yonside.compat")
}

compat {
    minecraft("1.16.5")
}
