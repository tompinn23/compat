package dev.example.compat.api;

/**
 * Injected onto each version's World class by a mixin, so core code can do {@code (CompatWorld) world}
 * with no wrapper objects. Every method carries the {@code compat$} prefix so it can never collide with
 * a Minecraft method, whichever names (MCP, SRG, official) are in effect.
 */
public interface CompatWorld {

    boolean compat$isClientSide();
}
