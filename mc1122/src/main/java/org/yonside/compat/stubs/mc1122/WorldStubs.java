package org.yonside.compat.stubs.mc1122;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.yonside.compat.api.CompatBlock;
import org.yonside.compat.api.CompatWorld;
import org.yonside.compat.api.Pos;
import org.yonside.compat.stub.StubFor;

/** {@link CompatWorld} on 1.12.2. Unwrapped into static methods and linked into each mod that uses it. */
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

    @Override
    public void setBlock(long pos, CompatBlock block) {
        world.setBlockState(toBlockPos(pos), ((Block) (Object) block).getDefaultState());
    }

    @Override
    public int redstonePower(long pos) {
        return world.getRedstonePowerFromNeighbors(toBlockPos(pos));
    }

    static BlockPos toBlockPos(long pos) {
        return new BlockPos(Pos.x(pos), Pos.y(pos), Pos.z(pos));
    }
}
