package org.yonside.compat.mc1165.mixin;

import org.yonside.compat.api.CompatWorld;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// Architectury Loom applies Mojang mappings to classes as well as members, so 1.16.5's World is
// net.minecraft.world.level.Level here and its isRemote field is isClientSide.
@Mixin(Level.class)
public abstract class MixinLevel implements CompatWorld {

    @Shadow @Final public boolean isClientSide;

    @Override
    public boolean compat$isClientSide() {
        return this.isClientSide;
    }
}
