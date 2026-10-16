package org.yonside.compat.example.mc1710;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yonside.compat.example.ExampleMachine;

/**
 * 1.7.10 half of the example: declare the mod, find a world, hand it to the shared ExampleMachine.
 * modid has to be a compile-time constant, so it is spelled out here as well as in
 * gradle.properties' example_mod_id; everything else comes from mcmod.info via useMetadata.
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
        // 1.7.10 exposes the world as a public field; 1.12.2 and 1.16.5 use a getter.
        for (String line : machine.report(event.world)) {
            LOG.info(line);
        }
    }
}
