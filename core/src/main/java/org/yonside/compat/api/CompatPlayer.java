package org.yonside.compat.api;

/** A player, whatever the version calls it. Linked like {@link CompatWorld}. */
public interface CompatPlayer {

    /** Shows {@code text} in the player's chat. */
    void sendMessage(String text);
}
