package org.yonside.compat.gradle;

import java.util.Map;

import javax.inject.Inject;

import com.gtnewhorizons.retrofuturagradle.MinecraftExtension;
import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

/**
 * The {@code compat {}} block. Every project of a mod applies {@code org.yonside.compat} and picks one
 * role, much like Architectury's common and platform projects:
 *
 * <pre>
 * // common/build.gradle.kts -- plain Java 21, no Minecraft or Forge
 * compat {
 *     common()
 *     mod { modId = "mymod"; license = "MIT" }
 * }
 *
 * // mc1710/build.gradle.kts -- Minecraft 1.7.10 and Forge via RetroFuturaGradle
 * compat { minecraft("1.7.10") }
 * </pre>
 *
 * A version project embeds its common project's jar, compiles against Minecraft and Forge, generates
 * whatever {@code @Mod} class and metadata the author has not written, downgrades the result to Java 8
 * and publishes {@code <modId>-<minecraft>}. See {@link CompatPlugin}.
 */
public abstract class CompatExtension {

    public static final String NAME = "compat";

    enum Role { COMMON, MINECRAFT }

    private final Project project;
    private final ModInfo mod;
    private Role role;
    private MinecraftTarget target;
    private String commonPath;
    private CompatExtension common;
    private boolean mixins;

    @Inject
    public CompatExtension(Project project) {
        this.project = project;
        this.mod = project.getObjects().newInstance(ModInfo.class);
        mod.getVersion().convention(project.provider(() -> project.getVersion().toString()));
        getShadePath().convention(mod.getModId().map(modId ->
            project.getGroup().toString().replace('.', '/') + "/" + modId + "/jvmdg/"));
    }

    /** What the mod loader is told about the mod. Set it in the common project; version projects inherit it. */
    public ModInfo getMod() {
        return mod;
    }

    public void mod(Action<? super ModInfo> action) {
        action.execute(mod);
    }

    /**
     * Where JvmDowngrader's stubs are relocated inside this project's Java 8 jar. Must be unique to the
     * mod. Defaults to the project group as a path, then the mod id, then {@code jvmdg/}.
     */
    public abstract Property<String> getShadePath();

    /** Makes this the mod's common project: Java 21, no Minecraft or Forge, embedded in every version's jar. */
    public void common() {
        claimRole(Role.COMMON, "common()");
        CommonSetup.apply(project, this);
    }

    /**
     * The common project for {@link #minecraft}, by path. Defaults to a sibling project named
     * {@code common}. Must come before {@code minecraft(...)}.
     */
    public void commonProject(String path) {
        if (role == Role.MINECRAFT) {
            throw new GradleException("compat.commonProject(\"" + path + "\") must come before minecraft(...) in " + project);
        }
        commonPath = path;
    }

    /**
     * Makes this a version project for {@code version}, e.g. {@code "1.7.10"}: applies RetroFuturaGradle
     * or Architectury Loom, and builds and publishes the mod's jar for that version.
     */
    public void minecraft(String version) {
        MinecraftTarget target;
        try {
            target = MinecraftTarget.of(version);
        } catch (IllegalArgumentException e) {
            throw new GradleException("compat.minecraft: " + e.getMessage());
        }
        claimRole(Role.MINECRAFT, "minecraft(\"" + version + "\")");
        this.target = target;
        Project commonProject = findCommonProject();

        // The common project's mod {} block has to be set before this project reads it.
        project.evaluationDependsOn(commonProject.getPath());
        common = commonProject.getExtensions().findByType(CompatExtension.class);
        if (common == null || common.role != Role.COMMON) {
            throw new GradleException(commonProject + " is not a common project: apply org.yonside.compat there"
                + " and call compat { common() }.");
        }
        mod.inheritFrom(common.mod);
        PlatformSetup.apply(project, this, commonProject);
    }

    /**
     * Enables Mixin with the defaults: config {@code mixins.<modId>.json} (hand-written, in this
     * project's resources), refmap {@code mixins.<modId>.refmap.json}, and on 1.7.10 / 1.12.2 the
     * UniMixins / MixinBooter dev jar, which the metadata then lists as a required mod.
     */
    public void mixins() {
        mixins(options -> {});
    }

    public void mixins(Action<? super MixinOptions> action) {
        requireMinecraft("mixins");
        if (mixins) {
            throw new GradleException("compat.mixins is already enabled in " + project);
        }
        mixins = true;
        MixinOptions options = project.getObjects().newInstance(MixinOptions.class);
        String modId = mod.getModId().get();
        options.getConfig().convention("mixins." + modId + ".json");
        options.getRefmap().convention("mixins." + modId + ".refmap.json");
        if (target.mixinProvider != null) {
            options.getProvider().convention(target.mixinProvider.dependency());
        }
        action.execute(options);
        PlatformSetup.enableMixins(project, this, options);
    }

    /** 1.7.10 / 1.12.2: registers {@code className} as the jar's FML coremod, in production and in dev runs. */
    public void coremod(String className) {
        requireMinecraft("coremod");
        if (target.toolchain != MinecraftTarget.Toolchain.RFG) {
            throw new GradleException("compat.coremod: Minecraft " + target.version + " has no FML coremods.");
        }
        RfgSetup.coremod(project, className);
    }

    /**
     * Installs another version project's mod (its downgraded dev-name jar) in this project's dev runs,
     * e.g. {@code devRunMod(":example:mc1710")}, the way a player adds a jar to mods/.
     */
    public void devRunMod(String projectPath) {
        requireMinecraft("devRunMod");
        project.getDependencies().add(PlatformSetup.DEV_RUN_MODS,
            project.getDependencies().project(Map.of("path", projectPath, "configuration", PlatformSetup.DEV_MOD_ELEMENTS)));
    }

    /** Escape hatch: configures RetroFuturaGradle's {@code minecraft} extension (1.7.10, 1.12.2). */
    public void rfg(Action<? super MinecraftExtension> action) {
        requireMinecraft("rfg");
        if (target.toolchain != MinecraftTarget.Toolchain.RFG) {
            throw new GradleException("compat.rfg: Minecraft " + target.version + " is built with Loom; use compat.loom.");
        }
        action.execute(project.getExtensions().getByType(MinecraftExtension.class));
    }

    /** Escape hatch: configures Architectury Loom's {@code loom} extension (1.16.5). */
    public void loom(Action<? super LoomGradleExtensionAPI> action) {
        requireMinecraft("loom");
        if (target.toolchain != MinecraftTarget.Toolchain.LOOM) {
            throw new GradleException("compat.loom: Minecraft " + target.version + " is built with RetroFuturaGradle; use compat.rfg.");
        }
        action.execute(project.getExtensions().getByType(LoomGradleExtensionAPI.class));
    }

    /** The version this project builds, or null if it is not a version project. */
    public MinecraftTarget getTarget() {
        return target;
    }

    /** The common project's block for a version project, else null. */
    ModInfo inheritedMod() {
        return common == null ? null : common.mod;
    }

    private Project findCommonProject() {
        if (commonPath != null) {
            Project found = project.findProject(commonPath);
            if (found == null) {
                throw new GradleException("compat.commonProject: no project " + commonPath);
            }
            return found;
        }
        Project parent = project.getParent();
        Project sibling = parent == null ? null : parent.getChildProjects().get("common");
        if (sibling == null) {
            throw new GradleException("compat.minecraft: " + project + " has no sibling project named 'common'."
                + " Name its common project with compat.commonProject(\":path\") first.");
        }
        return sibling;
    }

    private void claimRole(Role wanted, String call) {
        if (role != null) {
            throw new GradleException("compat." + call + ": " + project + " is already a "
                + (role == Role.COMMON ? "common project" : "Minecraft " + target.version + " project")
                + ". Each project has exactly one role.");
        }
        role = wanted;
    }

    private void requireMinecraft(String call) {
        if (role != Role.MINECRAFT) {
            throw new GradleException("compat." + call + " needs compat.minecraft(...) first in " + project);
        }
    }
}
