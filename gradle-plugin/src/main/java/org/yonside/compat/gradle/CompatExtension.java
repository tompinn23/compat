package org.yonside.compat.gradle;

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
 * whatever {@code @Mod} class and metadata the author has not written, links the result against
 * Compat's stubs for the version, downgrades it to Java 8 and publishes {@code <modId>-<minecraft>}.
 * See {@link CompatPlugin}.
 */
public abstract class CompatExtension {

    public static final String NAME = "compat";

    enum Role { COMMON, MINECRAFT, STUBS }

    private final Project project;
    private final ModInfo mod;
    private Role role;
    private MinecraftTarget target;
    private String commonPath;
    private CompatExtension common;

    @Inject
    public CompatExtension(Project project) {
        this.project = project;
        this.mod = project.getObjects().newInstance(ModInfo.class);
        mod.getVersion().convention(project.provider(() -> project.getVersion().toString()));
        getLinkPath().convention(mod.getModId().map(modId ->
            project.getGroup().toString().replace('.', '/') + "/" + modId + "/compat/"));
    }

    /** What the mod loader is told about the mod. Set it in the common project; version projects inherit it. */
    public ModInfo getMod() {
        return mod;
    }

    public void mod(Action<? super ModInfo> action) {
        action.execute(mod);
    }

    /**
     * Where the Compat classes this mod uses (stubs, API value types) are copied into its jar by
     * linking, with JvmDowngrader's under {@code jvmdg/} inside. Must be unique to the mod. Defaults to
     * the project group as a path, then the mod id, then {@code compat/}.
     */
    public abstract Property<String> getLinkPath();

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
        if (role == Role.MINECRAFT || role == Role.STUBS) {
            throw new GradleException("compat.commonProject(\"" + path + "\") must come before minecraft(...) or stubs(...) in " + project);
        }
        commonPath = path;
    }

    /**
     * Makes this a version project for {@code version}, e.g. {@code "1.7.10"}: applies RetroFuturaGradle
     * or Architectury Loom, and builds and publishes the mod's jar for that version.
     */
    public void minecraft(String version) {
        PlatformSetup.apply(project, this, versionRole(Role.MINECRAFT, "minecraft", version));
    }

    /**
     * Makes this Compat's stubs for {@code version}: the API implemented against that version with
     * {@link org.yonside.compat.stub.StubFor} classes, which mods are linked against. Compat's own
     * projects only.
     */
    public void stubs(String version) {
        PlatformSetup.applyStubs(project, this, versionRole(Role.STUBS, "stubs", version));
    }

    /** Claims a version role and returns the common project, evaluated, whose mod {} block this inherits. */
    private Project versionRole(Role wanted, String call, String version) {
        MinecraftTarget target;
        try {
            target = MinecraftTarget.of(version);
        } catch (IllegalArgumentException e) {
            throw new GradleException("compat." + call + ": " + e.getMessage());
        }
        claimRole(wanted, call + "(\"" + version + "\")");
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
        return commonProject;
    }

    /**
     * 1.7.10: adds {@code runClient21} / {@code runServer21}, which run the game on Java 21 with
     * lwjgl3ify, loading the Java 21 dev jars directly -- what players on lwjgl3ify use the -java21 jar
     * for. They run in run/lwjgl3ify.
     */
    public void lwjgl3ify() {
        lwjgl3ify(RfgSetup.DEFAULT_LWJGL3IFY);
    }

    /** As {@link #lwjgl3ify()}, with a given lwjgl3ify version, e.g. {@code "3.0.33"}. */
    public void lwjgl3ify(String version) {
        requireMinecraft("lwjgl3ify");
        if (target != MinecraftTarget.V1_7_10) {
            throw new GradleException("compat.lwjgl3ify: lwjgl3ify is for Minecraft 1.7.10, not " + target.version + ".");
        }
        RfgSetup.lwjgl3ify(project, version);
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
            throw new GradleException("compat: " + project + " has no sibling project named 'common'."
                + " Name its common project with compat.commonProject(\":path\") first.");
        }
        return sibling;
    }

    private void claimRole(Role wanted, String call) {
        if (role != null) {
            throw new GradleException("compat." + call + ": " + project + " is already a "
                + switch (role) {
                    case COMMON -> "common project";
                    case MINECRAFT -> "Minecraft " + target.version + " project";
                    case STUBS -> "Minecraft " + target.version + " stubs project";
                }
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
