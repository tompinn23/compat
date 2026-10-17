package org.yonside.compat.gradle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.api.GradleException;

/**
 * A resolved, immutable copy of one version project's {@code mod {}} block merged over its common
 * project's. Built when the generate task's inputs are queried, so build scripts can keep setting
 * properties until then. Serializable because it is that task's input.
 */
public record ModSpec(
    String modId,
    String displayName,
    String description,
    String license,
    String version,
    List<String> authors,
    List<Dependency> dependencies
) implements Serializable {

    /**
     * Compat's artifact prefix (compatcore-common, compatcore-1.7.10, ...). Once a mod id, and still
     * reserved as one, though Compat no longer ships a mod: it is linked into each mod.
     */
    public static final String COMPAT_CORE = "compatcore";

    public record Dependency(String modId, String versionRange, boolean mandatory, String ordering, String side,
                             Set<MinecraftTarget> targets) implements Serializable {

        /** An empty target set means every targeted version. */
        public boolean appliesTo(MinecraftTarget target) {
            return targets.isEmpty() || targets.contains(target);
        }
    }

    /**
     * @param mod      the version project's block, which inherits its values from {@code inherited}
     * @param inherited the common project's block, or null; its requires come first, the version
     *                 project's replace any with the same id
     */
    public static ModSpec of(ModInfo mod, ModInfo inherited) {
        if (!mod.getModId().isPresent()) {
            throw new GradleException("compat.mod.modId is not set. Set it in the common project's compat { mod { } } block.");
        }
        String modId = mod.getModId().get();
        if (!modId.matches("[a-z][a-z0-9_]{1,63}")) {
            throw new GradleException("compat.mod.modId '" + modId + "' must be 2-64 characters of a-z, 0-9 and _,"
                + " starting with a letter: the strictest rule of any supported loader.");
        }

        Map<String, Dependency> dependencies = new LinkedHashMap<>();
        if (inherited != null) {
            inherited.getRequires().forEach(dep -> dependencies.put(dep.getName(), dependency(dep)));
        }
        mod.getRequires().forEach(dep -> dependencies.put(dep.getName(), dependency(dep)));
        dependencies.remove(modId);

        return new ModSpec(modId, mod.getDisplayName().getOrElse(modId), mod.getDescription().getOrElse(""),
            mod.getLicense().getOrNull(), mod.getVersion().get(), List.copyOf(mod.getAuthors().get()),
            List.copyOf(new ArrayList<>(dependencies.values())));
    }

    private static Dependency dependency(ModDependency dep) {
        String ordering = dep.getOrdering().get();
        if (!List.of("AFTER", "BEFORE", "NONE").contains(ordering)) {
            throw new GradleException("compat.mod.requires(\"" + dep.getName() + "\").ordering must be AFTER, BEFORE or NONE, was " + ordering);
        }
        Set<MinecraftTarget> targets = EnumSet.noneOf(MinecraftTarget.class);
        for (String version : dep.getMinecraft().get()) {
            try {
                targets.add(MinecraftTarget.of(version));
            } catch (IllegalArgumentException e) {
                throw new GradleException("compat.mod.requires(\"" + dep.getName() + "\").minecraft: " + e.getMessage());
            }
        }
        return new Dependency(dep.getName(), dep.getVersionRange().getOrNull(), dep.getMandatory().get(),
            ordering, dep.getSide().get(), Set.copyOf(targets));
    }
}
