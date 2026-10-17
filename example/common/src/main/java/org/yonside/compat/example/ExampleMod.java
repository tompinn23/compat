package org.yonside.compat.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yonside.compat.api.CompatAddon;
import org.yonside.compat.api.CompatMod;
import org.yonside.compat.api.CompatRegistry;
import org.yonside.compat.api.CompatWorld;
import org.yonside.compat.api.Pos;

/**
 * The example's mod class: no Minecraft or Forge imports, no per-version modules. {@code @CompatMod} is
 * how the compat Gradle plugin finds it; the plugin then generates each version's {@code @Mod} class,
 * which constructs this class and forwards its lifecycle, plus the metadata.
 */
@CompatMod
public final class ExampleMod implements CompatAddon {

    // log4j ships with all three Minecraft versions; the build compiles against the oldest API.
    static final Logger LOG = LogManager.getLogger("CompatExample");

    // Created at registration, the point every version expects blocks to come into being.
    private static MachineBlock machineBlock;

    private final ExampleMachine machine = new ExampleMachine(8, 64, -8);

    @Override
    public void onRegister(CompatRegistry registry) {
        machineBlock = new MachineBlock();
        registry.block("machine", machineBlock);
    }

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

    /** Places two machines, one on the other: the second's arrival is a neighbour change for the first. */
    @Override
    public void onServerStarted(CompatWorld overworld) {
        long pos = Pos.pack(8, 64, -8);
        overworld.setBlock(pos, machineBlock);
        overworld.setBlock(Pos.offset(pos, 0, 1, 0), machineBlock);
    }
}
