package org.yonside.compat.stubs.mc1165;

import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.RegistryEvent;
import org.yonside.compat.api.CompatBlock;
import org.yonside.compat.api.CompatRegistry;
import org.yonside.compat.stub.StubFor;

/**
 * {@link CompatRegistry} on 1.16.5: the block registry event, on the mod's own event bus, so entries are
 * named after the mod.
 */
@StubFor(CompatRegistry.class)
public record RegistryStubs(RegistryEvent.Register<Block> event) implements CompatRegistry {

    @Override
    public void block(String name, CompatBlock block) {
        event.getRegistry().register(((Block) (Object) block).setRegistryName(name));
    }
}
