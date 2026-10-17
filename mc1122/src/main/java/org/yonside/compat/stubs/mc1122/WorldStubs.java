package org.yonside.compat.stubs.mc1122;

import net.minecraft.world.World;
import org.yonside.compat.api.CompatWorld;
import org.yonside.compat.stub.StubFor;

/** {@link CompatWorld} on 1.12.2, linked into each mod that uses it. */
@StubFor(api = CompatWorld.class, target = World.class)
public final class WorldStubs {

    private WorldStubs() {}

    public static boolean isClientSide(World world) {
        return world.isRemote;
    }

    public static long totalTime(World world) {
        return world.getTotalWorldTime();
    }
}
