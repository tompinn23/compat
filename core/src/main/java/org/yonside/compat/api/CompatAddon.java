package org.yonside.compat.api;

/**
 * The methods a mod built with Compat can implement, written once for every Minecraft version.
 *
 * <p>Annotate the implementation with {@link CompatMod} and apply the {@code org.yonside.compat} Gradle
 * plugin. For each Minecraft version the plugin generates the real {@code @Mod} class, which constructs
 * this class and calls these methods from that mod's own event handlers. Implementations need a public
 * no-argument constructor.
 */
public interface CompatAddon {

    /**
     * Registers the mod's content, at the version's registration point: FML pre-init on 1.7.10, the
     * block registry event on 1.12.2 and 1.16.5. Called before {@link #onInit}.
     */
    default void onRegister(CompatRegistry registry) {}

    /** Once, in this mod's FML init (1.7.10, 1.12.2) or common setup (1.16.5). */
    default void onInit() {}

    /** Whenever a world (1.16.5: level) loads, on either side. */
    default void onWorldLoad(CompatWorld world) {}

    /** Once a server, integrated or dedicated, has started, with its overworld. */
    default void onServerStarted(CompatWorld overworld) {}
}
