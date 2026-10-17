package org.yonside.compat.stubs.mc1122;

import net.minecraft.block.Block;
import net.minecraftforge.event.RegistryEvent;
import org.yonside.compat.api.CompatBlock;
import org.yonside.compat.api.CompatRegistry;
import org.yonside.compat.stub.StubFor;

/**
 * {@link CompatRegistry} on 1.12.2: the block registry event. Forge names entries after the mod whose
 * listener is running, which is the mod being registered.
 */
@StubFor(CompatRegistry.class)
public record RegistryStubs(RegistryEvent.Register<Block> event) implements CompatRegistry {

    @Override
    public void block(String name, CompatBlock block) {
        event.getRegistry().register(((Block) (Object) block).setRegistryName(name));
    }
}
