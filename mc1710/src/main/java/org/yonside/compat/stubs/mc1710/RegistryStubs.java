package org.yonside.compat.stubs.mc1710;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import org.yonside.compat.api.CompatBlock;
import org.yonside.compat.api.CompatRegistry;
import org.yonside.compat.stub.StubFor;

/** {@link CompatRegistry} on 1.7.10: registration happens in pre-init, through GameRegistry. */
@StubFor(CompatRegistry.class)
public record RegistryStubs(FMLPreInitializationEvent event) implements CompatRegistry {

    @Override
    public void block(String name, CompatBlock block) {
        Block real = (Block) (Object) block;
        real.setBlockName(event.getModMetadata().modId + "." + name);
        GameRegistry.registerBlock(real, name);
    }
}
