package org.yonside.compat.api;

/**
 * Where a mod registers its content, handed to {@link CompatAddon#onRegister} at each version's
 * registration point. Linked like {@link CompatWorld}.
 */
public interface CompatRegistry {

    /** Registers {@code block} under this mod's id as {@code name}, e.g. {@code "machine"}. */
    void block(String name, CompatBlock block);
}
