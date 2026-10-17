package org.yonside.compat.stubs.mc1122;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.yonside.compat.api.BlockHooks;
import org.yonside.compat.api.BlockSpec;
import org.yonside.compat.api.CompatBlock;
import org.yonside.compat.api.CompatPlayer;
import org.yonside.compat.api.CompatWorld;
import org.yonside.compat.api.Pos;
import org.yonside.compat.stub.StubFor;

/**
 * {@link CompatBlock} on 1.12.2: what a mod's block class extends once linked. Implementing
 * {@link BlockHooks} makes the compiler check the hooks match the API's; Minecraft's own methods are
 * final here and only call them.
 */
@StubFor(CompatBlock.class)
public abstract class LinkedBlock extends Block implements BlockHooks {

    protected LinkedBlock(BlockSpec spec) {
        super(switch (spec.material()) {
            case STONE -> Material.ROCK;
            case METAL -> Material.IRON;
            case WOOD -> Material.WOOD;
        });
        setHardness(spec.hardness());
        setLightLevel(spec.light() / 15f);
    }

    @Override
    @SuppressWarnings("deprecation")
    public final void neighborChanged(IBlockState state, World world, BlockPos pos, Block neighbor, BlockPos from) {
        onNeighborChanged((CompatWorld) world, Pos.pack(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    public final boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                          EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        return onUse((CompatWorld) world, Pos.pack(pos.getX(), pos.getY(), pos.getZ()), (CompatPlayer) player);
    }
}
