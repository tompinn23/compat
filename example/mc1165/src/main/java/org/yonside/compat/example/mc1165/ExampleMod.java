package org.yonside.compat.example.mc1165;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yonside.compat.example.ExampleMachine;

/**
 * 1.16.5 half of the example. Registration moved to the mod constructor and the mod's metadata to
 * mods.toml, but ExampleMachine is byte-for-byte the same code as on 1.7.10 and 1.12.2.
 */
@Mod("compatexample")
public class ExampleMod {

    private static final Logger LOG = LogManager.getLogger("CompatExample");

    private final ExampleMachine machine = new ExampleMachine(8, 64, -8);

    public ExampleMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        // getWorld() is typed as the broader LevelAccessor here; the cast to CompatWorld inside
        // ExampleMachine still works because the instance is a Level, which the mixin targets.
        for (String line : machine.report(event.getWorld())) {
            LOG.info(line);
        }
    }
}
