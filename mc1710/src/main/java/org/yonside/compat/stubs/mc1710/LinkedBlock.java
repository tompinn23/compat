package org.yonside.compat.stubs.mc1710;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.yonside.compat.api.BlockHooks;
import org.yonside.compat.api.BlockSpec;
import org.yonside.compat.api.CompatBlock;
import org.yonside.compat.api.CompatPlayer;
import org.yonside.compat.api.CompatWorld;
import org.yonside.compat.api.Pos;
import org.yonside.compat.stub.StubFor;

/**
 * {@link CompatBlock} on 1.7.10: what a mod's block class extends once linked. Implementing
 * {@link BlockHooks} makes the compiler check the hooks match the API's; Minecraft's own methods are
 * final here and only call them.
 */
@StubFor(CompatBlock.class)
public abstract class LinkedBlock extends Block implements BlockHooks {

    protected LinkedBlock(BlockSpec spec) {
        super(switch (spec.material()) {
            case STONE -> Material.rock;
            case METAL -> Material.iron;
            case WOOD -> Material.wood;
        });
        setHardness(spec.hardness());
        setLightLevel(spec.light() / 15f);
    }

    @Override
    public final void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        onNeighborChanged((CompatWorld) world, Pos.pack(x, y, z));
    }

    @Override
    public final boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                          int side, float hitX, float hitY, float hitZ) {
        return onUse((CompatWorld) world, Pos.pack(x, y, z), (CompatPlayer) player);
    }
}
