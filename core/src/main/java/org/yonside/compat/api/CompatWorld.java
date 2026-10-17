package org.yonside.compat.api;

/**
 * A Minecraft world, whatever the version calls it. Never implemented at runtime: the compat plugin
 * links each mod so that this type is the version's world class itself and each method is a static
 * call to that version's stub (see {@link org.yonside.compat.stub.StubFor}).
 *
 * <p>Interfaces the plugin maps this way must only declare abstract methods.
 */
public interface CompatWorld {

    /** True on the client's copy of the world, false on the server's. */
    boolean isClientSide();

    /** Ticks since the world was created. */
    long totalTime();
}
