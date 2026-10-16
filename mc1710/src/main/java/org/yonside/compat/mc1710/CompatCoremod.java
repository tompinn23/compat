package org.yonside.compat.mc1710;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coremod so the Compat* mixins apply to vanilla classes (World, Block, ...) before they load.
 * Found via the FMLCorePlugin manifest entry in production and -Dfml.coreMods.load in dev.
 * IEarlyMixinLoader is the GTNHMixins API, provided by UniMixins.
 */
@IFMLLoadingPlugin.Name("CompatCore")
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class CompatCoremod implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.compatcore.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return Arrays.asList("MixinWorld");
    }

    @Override public String[] getASMTransformerClass() { return new String[0]; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) {}
    @Override public String getAccessTransformerClass() { return null; }
}
