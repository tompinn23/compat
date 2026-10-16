package org.yonside.compat.mc1122;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Coremod so the Compat* mixins apply to vanilla classes before they load.
 * MixinBooter 11 marks IEarlyMixinLoader @Deprecated in favour of an IMixinConnector, but it still
 * works, and unlike a connector it also gets picked up in dev via -Dfml.coreMods.load.
 */
@IFMLLoadingPlugin.Name("CompatCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@SuppressWarnings("deprecation")
public class CompatCoremod implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.compatcore.json");
    }

    @Override public String[] getASMTransformerClass() { return new String[0]; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) {}
    @Override public String getAccessTransformerClass() { return null; }
}
