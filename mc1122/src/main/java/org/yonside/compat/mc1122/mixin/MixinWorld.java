package org.yonside.compat.mc1122.mixin;

import org.yonside.compat.api.CompatWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(World.class)
public abstract class MixinWorld implements CompatWorld {

    @Shadow @Final public boolean isRemote;

    @Override
    public boolean compat$isClientSide() {
        return this.isRemote;
    }
}
