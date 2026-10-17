package org.yonside.compat.api;

/**
 * What a block can react to, written once for every Minecraft version. {@link CompatBlock} implements
 * it for mods to override, and each version's block stub implements it too, so the compiler checks the
 * two always agree.
 */
public interface BlockHooks {

    /** A block next to this one changed, e.g. was placed, broken or powered. Server and client. */
    default void onNeighborChanged(CompatWorld world, long pos) {}

    /** A player right-clicked this block. Return true if that did something, which stops further handling. */
    default boolean onUse(CompatWorld world, long pos, CompatPlayer player) {
        return false;
    }
}
