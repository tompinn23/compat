package org.yonside.compat.api;

/**
 * A Minecraft world, whatever the version calls it. Never implemented at runtime: the compat plugin
 * links each mod so that this type is the version's world class itself and each method is a static
 * call to that version's stub (see {@link org.yonside.compat.stub.StubFor}).
 */
public interface CompatWorld {

    /** True on the client's copy of the world, false on the server's. */
    boolean isClientSide();

    /** Ticks since the world was created. */
    long totalTime();

    /** Places {@code block} (in its default state) at {@code pos}, notifying the blocks around it. */
    void setBlock(long pos, CompatBlock block);

    /** The redstone power reaching {@code pos} from its neighbours, 0 to 15. */
    int redstonePower(long pos);
}
