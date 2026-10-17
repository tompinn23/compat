package org.yonside.compat.stubs.mc1165;

import net.minecraft.world.level.Level;
import org.yonside.compat.api.CompatWorld;
import org.yonside.compat.stub.StubFor;

/**
 * {@link CompatWorld} on 1.16.5, linked into each mod that uses it. Loom maps classes as well as members
 * to Mojang's names, so the world is {@code Level} here; it is remapped to SRG with the rest of the jar.
 */
@StubFor(api = CompatWorld.class, target = Level.class)
public final class WorldStubs {

    private WorldStubs() {}

    public static boolean isClientSide(Level level) {
        return level.isClientSide;
    }

    public static long totalTime(Level level) {
        return level.getGameTime();
    }
}
