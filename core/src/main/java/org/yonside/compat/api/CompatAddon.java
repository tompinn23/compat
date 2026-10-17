package org.yonside.compat.api;

/**
 * The methods a mod built on Compat Core can implement, written once for every Minecraft version.
 *
 * <p>Annotate the implementation with {@link CompatMod} and apply the {@code org.yonside.compat.addon}
 * Gradle plugin. For each Minecraft version the plugin generates the real {@code @Mod} class, which
 * constructs this class and calls these methods from that mod's own event handlers. Implementations
 * need a public no-argument constructor.
 */
public interface CompatAddon {

    /** Once, in this mod's FML init (1.7.10, 1.12.2) or common setup (1.16.5). */
    default void onInit() {}

    /** Whenever a world (1.16.5: level) loads, on either side. */
    default void onWorldLoad(CompatWorld world) {}
}
