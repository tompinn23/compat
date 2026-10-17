package org.yonside.compat.example;

import org.yonside.compat.api.BlockMaterial;
import org.yonside.compat.api.BlockSpec;
import org.yonside.compat.api.CompatBlock;
import org.yonside.compat.api.CompatPlayer;
import org.yonside.compat.api.CompatWorld;
import org.yonside.compat.api.Pos;

/**
 * The example's block, written once. Linked into each version's jar it extends that version's
 * {@code LinkedBlock}, so it is a real Minecraft {@code Block} and Minecraft calls these overrides.
 */
public final class MachineBlock extends CompatBlock {

    public MachineBlock() {
        super(BlockSpec.of(BlockMaterial.METAL).hardness(3f).light(7));
    }

    @Override
    public void onNeighborChanged(CompatWorld world, long pos) {
        ExampleMod.LOG.info("machine at " + describe(pos) + " saw a neighbour change, power " + world.redstonePower(pos));
    }

    @Override
    public boolean onUse(CompatWorld world, long pos, CompatPlayer player) {
        if (!world.isClientSide()) {
            player.sendMessage("Compat Example machine at " + describe(pos));
        }
        return true;
    }

    private static String describe(long pos) {
        return "(" + Pos.x(pos) + ", " + Pos.y(pos) + ", " + Pos.z(pos) + ")";
    }
}
