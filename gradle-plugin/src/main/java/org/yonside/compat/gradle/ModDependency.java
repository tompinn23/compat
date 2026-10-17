package org.yonside.compat.gradle;

import javax.inject.Inject;

import org.gradle.api.Named;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

/**
 * Another mod this one needs, written into every version's generated metadata: the generated
 * {@code @Mod}'s dependencies string and mcmod.info on 1.7.10 and 1.12.2, mods.toml's [[dependencies]]
 * on 1.16.5.
 */
public abstract class ModDependency implements Named {

    private final String name;

    @Inject
    public ModDependency(String name) {
        this.name = name;
        getMandatory().convention(true);
        getOrdering().convention("NONE");
        getSide().convention("BOTH");
    }

    /** The other mod's id. */
    @Override
    public String getName() {
        return name;
    }

    /** Maven-style range such as {@code [0.1.0,)}. Unset means any version. */
    public abstract Property<String> getVersionRange();

    public abstract Property<Boolean> getMandatory();

    /** {@code AFTER}, {@code BEFORE} or {@code NONE}: whether this mod loads after or before the other. */
    public abstract Property<String> getOrdering();

    /** mods.toml only: {@code BOTH}, {@code CLIENT} or {@code SERVER}. */
    public abstract Property<String> getSide();

    /** Minecraft versions this dependency applies to. Empty means every targeted version. */
    public abstract SetProperty<String> getMinecraft();
}
