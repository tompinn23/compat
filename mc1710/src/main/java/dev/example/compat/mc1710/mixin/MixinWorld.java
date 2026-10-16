package dev.example.compat.mc1710.mixin;

import dev.example.compat.api.CompatWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(World.class)
public abstract class MixinWorld implements CompatWorld {

    // Not final in 1.7.10 (WorldClient sets it after super()), so no @Final here.
    @Shadow public boolean isRemote;

    @Override
    public boolean compat$isClientSide() {
        return this.isRemote;
    }
}
