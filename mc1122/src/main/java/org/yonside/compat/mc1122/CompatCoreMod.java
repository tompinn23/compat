package org.yonside.compat.mc1122;

import net.minecraftforge.fml.common.Mod;

/**
 * Registers Compat Core as a mod so addons can depend on it. Addons are driven by their own generated
 * @Mod classes, not from here. Metadata comes from mcmod.info via useMetadata.
 */
@Mod(modid = "compatcore", useMetadata = true)
public class CompatCoreMod {
}
