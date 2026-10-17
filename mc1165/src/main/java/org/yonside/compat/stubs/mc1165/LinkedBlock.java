package org.yonside.compat.stubs.mc1165;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import org.yonside.compat.api.BlockHooks;
import org.yonside.compat.api.BlockSpec;
import org.yonside.compat.api.CompatBlock;
import org.yonside.compat.api.CompatPlayer;
import org.yonside.compat.api.CompatWorld;
import org.yonside.compat.api.Pos;
import org.yonside.compat.stub.StubFor;

/**
 * {@link CompatBlock} on 1.16.5: what a mod's block class extends once linked. Implementing
 * {@link BlockHooks} makes the compiler check the hooks match the API's; Minecraft's own methods are
 * final here and only call them.
 */
@StubFor(CompatBlock.class)
public abstract class LinkedBlock extends Block implements BlockHooks {

    protected LinkedBlock(BlockSpec spec) {
        super(BlockBehaviour.Properties.of(switch (spec.material()) {
                case STONE -> Material.STONE;
                case METAL -> Material.METAL;
                case WOOD -> Material.WOOD;
            })
            .strength(spec.hardness())
            .lightLevel(state -> spec.light()));
    }

    @Override
    @SuppressWarnings("deprecation")
    public final void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from,
                                      boolean moving) {
        onNeighborChanged((CompatWorld) level, Pos.pack(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    @SuppressWarnings("deprecation")
    public final InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                       BlockHitResult hit) {
        boolean used = onUse((CompatWorld) level, Pos.pack(pos.getX(), pos.getY(), pos.getZ()), (CompatPlayer) player);
        return used ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
