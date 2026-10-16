package org.yonside.compat.example.mc1122;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yonside.compat.example.ExampleMachine;

/**
 * 1.12.2 half of the example. Same shape as the 1.7.10 module -- only the FML package names and the
 * way you reach the world differ. Everything with actual behaviour lives in ExampleMachine.
 */
@Mod(modid = "compatexample", useMetadata = true)
public class ExampleMod {

    private static final Logger LOG = LogManager.getLogger("CompatExample");

    private final ExampleMachine machine = new ExampleMachine(8, 64, -8);

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        for (String line : machine.report(event.getWorld())) {
            LOG.info(line);
        }
    }
}
