package org.yonside.compat.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yonside.compat.api.CompatAddon;
import org.yonside.compat.api.CompatMod;
import org.yonside.compat.api.CompatWorld;

/**
 * The example's entire mod: one class, no Minecraft or Forge imports, no per-version modules.
 * {@code @CompatMod} is how the addon Gradle plugin finds it; the plugin then generates each version's
 * {@code @Mod} class, which constructs this class and forwards its lifecycle, plus the metadata.
 */
@CompatMod
public final class ExampleMod implements CompatAddon {

    // log4j ships with all three Minecraft versions; the build compiles against the oldest API.
    private static final Logger LOG = LogManager.getLogger("CompatExample");

    private final ExampleMachine machine = new ExampleMachine(8, 64, -8);

    @Override
    public void onInit() {
        LOG.info("Compat Example initialised, machine at " + machine.position());
    }

    @Override
    public void onWorldLoad(CompatWorld world) {
        for (String line : machine.report(world)) {
            LOG.info(line);
        }
    }
}
