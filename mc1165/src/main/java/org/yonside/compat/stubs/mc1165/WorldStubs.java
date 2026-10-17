package org.yonside.compat.stubs.mc1165;

import net.minecraft.world.level.Level;
import org.yonside.compat.api.CompatWorld;
import org.yonside.compat.stub.StubFor;

/**
 * {@link CompatWorld} on 1.16.5. Unwrapped into static methods and linked into each mod that uses it.
 * Loom maps classes as well as members to Mojang's names, so the world is {@code Level} here; it is
 * remapped to SRG with the rest of the mod's jar.
 */
@StubFor(CompatWorld.class)
public record WorldStubs(Level level) implements CompatWorld {

    @Override
    public boolean isClientSide() {
        return level.isClientSide;
    }

    @Override
    public long totalTime() {
        return level.getGameTime();
    }
}
