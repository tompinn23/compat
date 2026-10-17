package org.yonside.compat.api;

/**
 * A block, written once for every Minecraft version: extend this and override the {@link BlockHooks}.
 * Never instantiated as itself: the compat plugin links each mod so that this class is the version's
 * block stub, a real subclass of Minecraft's {@code Block} that calls the hooks, and the mod's block a
 * real {@code Block} too. Register it with {@link CompatRegistry#block}.
 */
public abstract class CompatBlock implements BlockHooks {

    protected CompatBlock(BlockSpec spec) {}
}
