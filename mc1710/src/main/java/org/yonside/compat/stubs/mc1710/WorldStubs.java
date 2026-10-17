package org.yonside.compat.stubs.mc1710;

import net.minecraft.world.World;
import org.yonside.compat.api.CompatWorld;
import org.yonside.compat.stub.StubFor;

/** {@link CompatWorld} on 1.7.10. Unwrapped into static methods and linked into each mod that uses it. */
@StubFor(CompatWorld.class)
public record WorldStubs(World world) implements CompatWorld {

    @Override
    public boolean isClientSide() {
        return world.isRemote;
    }

    @Override
    public long totalTime() {
        return world.getTotalWorldTime();
    }
}
