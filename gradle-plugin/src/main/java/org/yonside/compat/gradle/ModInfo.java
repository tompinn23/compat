package org.yonside.compat.gradle;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code compat { mod { } }} block: what a mod loader needs to know about the mod, rendered into each
 * version's mcmod.info or mods.toml. Declared once in the common project; a version project's block
 * inherits every value and may override any of them or add version-specific {@link #requires}.
 */
public abstract class ModInfo {

    /** Frozen once published: worlds and other mods refer to it. */
    public abstract Property<String> getModId();

    /** Defaults to the mod id. */
    public abstract Property<String> getDisplayName();

    public abstract Property<String> getDescription();

    /** Required by mods.toml, so required whenever 1.16.5 is built and its metadata generated. */
    public abstract Property<String> getLicense();

    /** Defaults to the project version. */
    public abstract Property<String> getVersion();

    public abstract ListProperty<String> getAuthors();

    public abstract NamedDomainObjectContainer<ModDependency> getRequires();

    /** Declares a mandatory dependency on any version of {@code modId}. */
    public ModDependency requires(String modId) {
        return getRequires().maybeCreate(modId);
    }

    /** Declares or adjusts a dependency; calling it again for the same id edits the existing one. */
    public ModDependency requires(String modId, Action<? super ModDependency> action) {
        ModDependency dependency = requires(modId);
        action.execute(dependency);
        return dependency;
    }

    /** Makes every value not set here fall back to {@code parent}'s. Requires are merged in {@link ModSpec}. */
    void inheritFrom(ModInfo parent) {
        getModId().convention(parent.getModId());
        getDisplayName().convention(parent.getDisplayName());
        getDescription().convention(parent.getDescription());
        getLicense().convention(parent.getLicense());
        getVersion().convention(parent.getVersion());
        getAuthors().convention(parent.getAuthors());
    }
}
