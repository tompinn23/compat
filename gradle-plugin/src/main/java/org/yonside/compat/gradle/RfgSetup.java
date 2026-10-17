package org.yonside.compat.gradle;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.gtnewhorizons.retrofuturagradle.MinecraftExtension;
import com.gtnewhorizons.retrofuturagradle.mcp.MCPTasks;
import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar;
import com.gtnewhorizons.retrofuturagradle.minecraft.MinecraftTasks;
import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask;
import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils;
import com.gtnewhorizons.retrofuturagradle.util.Distribution;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar;

/** RetroFuturaGradle (1.7.10, 1.12.2) for {@link PlatformSetup}. */
final class RfgSetup {

    static final String PLUGIN_ID = "com.gtnewhorizons.retrofuturagradle";
    static final String RUN_CLIENT_21 = "runClient21";
    static final String RUN_SERVER_21 = "runServer21";
    /** The lwjgl3ify GTNHGradle pins; see its UpdateableConstants. */
    static final String DEFAULT_LWJGL3IFY = "3.0.33";
    private static final String LWJGL3IFY = "com.github.GTNewHorizons:lwjgl3ify:";

    /**
     * What GTNHGradle's modern-Java runs pass the JVM: the RetroFuturaBootstrap class loader, and the
     * opens 1.7.10, Forge and lwjgl3ify need on Java 17+ (GTNHGradle's ModernJavaModule.JAVA_17_ARGS).
     */
    private static final List<String> MODERN_JAVA_ARGS = List.of("-Dfile.encoding=UTF-8",
        "-Djava.system.class.loader=com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader",
        "--enable-native-access", "ALL-UNNAMED",
        "--add-opens", "java.base/java.io=ALL-UNNAMED", "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.ref=ALL-UNNAMED", "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.net.spi=ALL-UNNAMED",
        "--add-opens", "java.base/java.net=ALL-UNNAMED", "--add-opens", "java.base/java.nio.channels=ALL-UNNAMED",
        "--add-opens", "java.base/java.nio.charset=ALL-UNNAMED", "--add-opens", "java.base/java.nio.file=ALL-UNNAMED",
        "--add-opens", "java.base/java.nio=ALL-UNNAMED", "--add-opens", "java.base/java.text=ALL-UNNAMED",
        "--add-opens", "java.base/java.time.chrono=ALL-UNNAMED", "--add-opens", "java.base/java.time.format=ALL-UNNAMED",
        "--add-opens", "java.base/java.time.temporal=ALL-UNNAMED", "--add-opens", "java.base/java.time.zone=ALL-UNNAMED",
        "--add-opens", "java.base/java.time=ALL-UNNAMED", "--add-opens", "java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.concurrent.locks=ALL-UNNAMED", "--add-opens", "java.base/java.util.jar=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.zip=ALL-UNNAMED", "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/jdk.internal.loader=ALL-UNNAMED", "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-opens", "java.base/jdk.internal.ref=ALL-UNNAMED", "--add-opens", "java.base/jdk.internal.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens", "java.desktop/com.sun.imageio.plugins.png=ALL-UNNAMED",
        "--add-opens", "java.desktop/sun.awt.image=ALL-UNNAMED", "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
        "--add-opens", "java.sql.rowset/javax.sql.rowset.serial=ALL-UNNAMED",
        "--add-opens", "jdk.dynalink/jdk.dynalink.beans=ALL-UNNAMED",
        "--add-opens", "jdk.naming.dns/com.sun.jndi.dns=ALL-UNNAMED,java.naming");

    private RfgSetup() {}

    static void applyToolchain(Project project, CompatExtension ext) {
        MinecraftTarget target = ext.getTarget();
        project.getPluginManager().apply(PLUGIN_ID);

        // RFG decompiles and runs Minecraft on Java 8, and wants Azul's build of it.
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(8));
        java.getToolchain().getVendor().set(JvmVendorSpec.AZUL);

        MinecraftExtension minecraft = minecraft(project);
        minecraft.getMcVersion().set(target.version);
        minecraft.getUsername().set("Developer");
        if (target.mappingChannel != null) {
            minecraft.getMcpMappingChannel().set(target.mappingChannel);
            minecraft.getMcpMappingVersion().set(target.mappingVersion);
        }
    }

    static PlatformSetup.ReleaseJars configureJars(Project project, CompatExtension ext, TaskProvider<Jar> jar,
                                                   TaskProvider<Jar> linkedJar, TaskProvider<ShadeJar> shade,
                                                   Configuration embed) {
        // RFG reobfuscates the unlinked jar too; it carries the Mixin mappings the others reuse.
        TaskProvider<ReobfuscatedJar> reobfJar = project.getTasks().named("reobfJar", ReobfuscatedJar.class,
            task -> task.getArchiveClassifier().set("unlinked-srg"));
        // Both created by RFG's reobf<JarTask> rule.
        TaskProvider<ReobfuscatedJar> reobfLinked = project.getTasks().named(
            "reobf" + Character.toUpperCase(PlatformSetup.DEV_JAR.charAt(0)) + PlatformSetup.DEV_JAR.substring(1),
            ReobfuscatedJar.class, task -> {
                task.getArchiveClassifier().set("java21");
                task.getExtraSrgFiles().from(reobfJar.map(ReobfuscatedJar::getExtraSrgFiles));
                task.getExtraSrgEntries().addAll(reobfJar.flatMap(ReobfuscatedJar::getExtraSrgEntries));
            });
        TaskProvider<ReobfuscatedJar> reobfShaded = project.getTasks().named("reobfShadeDowngradedApi", ReobfuscatedJar.class, task -> {
            task.getArchiveClassifier().set("");
            task.getExtraSrgFiles().from(reobfJar.map(ReobfuscatedJar::getExtraSrgFiles));
            task.getExtraSrgEntries().addAll(reobfJar.flatMap(ReobfuscatedJar::getExtraSrgEntries));
        });

        // Dev runs use Java 8, so swap the unlinked jar and embedded common jar for the linked, downgraded jar.
        Provider<RegularFile> devJar = jar.flatMap(Jar::getArchiveFile);
        FileCollection embedded = embed;
        for (String run : List.of("runClient", "runServer")) {
            project.getTasks().named(run, JavaExec.class, task -> {
                FileCollection original = project.files(((ConfigurableFileCollection) task.getClasspath()).getFrom().toArray());
                task.dependsOn(shade);
                task.setClasspath(project.files(shade).plus(
                    original.filter(file -> !PlatformSetup.isJava21Output(file, devJar, embedded))));
            });
        }
        return new PlatformSetup.ReleaseJars(reobfShaded, reobfLinked);
    }

    static void enableMixins(Project project, MinecraftTarget target, MixinOptions options) {
        MinecraftTarget.MixinProvider provider = target.mixinProvider;
        String providerNotation = options.getProvider().get();
        project.getRepositories().maven(repo -> {
            repo.setName("CompatMixin" + target.id);
            repo.setUrl(provider.repository());
        });

        for (String extra : provider.processorExtras()) {
            project.getDependencies().add("annotationProcessor", extra);
        }
        project.getDependencies().add("annotationProcessor", providerNotation);

        // Adds the refmap and RFG's reobfuscation mappings for the Mixin annotation processor.
        Object spec = project.getExtensions().getByType(ModUtils.class).enableMixins(providerNotation, options.getRefmap().get());
        Dependency dependency = project.getDependencies().add("implementation", spec);
        if (!provider.transitive() && dependency instanceof ModuleDependency module) {
            module.setTransitive(false);
        }

        minecraft(project).getExtraRunJvmArguments().addAll("-Dmixin.debug.export=true", "-Dmixin.checks.interfaces=true");
    }

    /**
     * Registers {@link #RUN_CLIENT_21} and {@link #RUN_SERVER_21}, set up the way GTNHGradle sets up its
     * runClient21 / runServer21: RFG's run task on a Java 21 launcher with LWJGL 3, lwjgl3ify's Forge
     * patches ahead of the patched Minecraft jar, and lwjgl3ify plus the mod's runtime classpath behind
     * them. The mod itself is its Java 21 dev jar, which runs as-is.
     */
    static void lwjgl3ify(Project project, String version) {
        project.getRepositories().maven(repo -> {
            repo.setName("GTNHLwjgl3ify");
            repo.setUrl("https://nexus.gtnewhorizons.com/repository/public/");
        });
        Configuration patches = project.getConfigurations().create("lwjgl3ifyPatches", c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.setTransitive(false);
            c.setDescription("lwjgl3ify's Forge patches, ahead of Minecraft on the Java 21 dev runs' classpath.");
        });
        Configuration runtime = project.getConfigurations().create("lwjgl3ifyRuntime", c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.extendsFrom(project.getConfigurations().getByName("runtimeClasspath"));
            c.setDescription("lwjgl3ify and the mod's runtime dependencies for the Java 21 dev runs.");
        });
        project.getDependencies().add(patches.getName(), LWJGL3IFY + version + ":forgePatches");
        project.getDependencies().add(runtime.getName(), LWJGL3IFY + version);

        MCPTasks mcpTasks = project.getExtensions().getByType(MCPTasks.class);
        MinecraftTasks mcTasks = project.getExtensions().getByType(MinecraftTasks.class);
        MinecraftExtension minecraft = minecraft(project);
        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        TaskProvider<Jar> jar = project.getTasks().named(PlatformSetup.DEV_JAR, Jar.class);
        // The linked dev jar holds the mod, its common jar and the Compat classes it uses: leave the
        // unlinked versions of all of those off the classpath.
        SourceSet main = project.getExtensions().getByType(SourceSetContainer.class).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        FileCollection embedded = project.files(project.getConfigurations().getByName(PlatformSetup.EMBED), main.getOutput());
        boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

        for (Distribution side : List.of(Distribution.CLIENT, Distribution.DEDICATED_SERVER)) {
            boolean client = side == Distribution.CLIENT;
            project.getTasks().register(client ? RUN_CLIENT_21 : RUN_SERVER_21, RunMinecraftTask.class, side).configure(task -> {
                task.setGroup("Modded Minecraft");
                task.setDescription("Runs the " + (client ? "client" : "dedicated server")
                    + " on Java 21 with lwjgl3ify, loading the Java 21 jars directly.");
                task.classpath(patches);
                task.classpath(mcpTasks.getTaskPackageMcLauncher());
                task.classpath(mcpTasks.getTaskPackagePatchedMc());
                task.classpath(mcpTasks.getPatchedConfiguration());
                task.classpath(jar);
                task.classpath(runtime.minus(embedded));
                task.setup(project);
                task.dependsOn(mcpTasks.getLauncherSources().getClassesTaskName(), mcTasks.getTaskDownloadVanillaAssets(),
                    mcpTasks.getTaskPackagePatchedMc(), jar);

                task.getMainClass().set(client ? "GradleStart" : "GradleStartServer");
                task.getUsername().set(minecraft.getUsername());
                if (!client) {
                    task.getExtraArgs().add("nogui");
                }
                task.getLwjglVersion().set(3);
                task.getExtraJvmArgs().addAll(MODERN_JAVA_ARGS);
                if (client && mac) {
                    task.getExtraJvmArgs().add("-XstartOnFirstThread");
                }
                // RetroFuturaBootstrap's entry points, as GTNHGradle uses.
                task.systemProperty("gradlestart.bouncerClient", "com.gtnewhorizons.retrofuturabootstrap.MainStartOnFirstThread");
                task.systemProperty("gradlestart.bouncerServer", "com.gtnewhorizons.retrofuturabootstrap.Main");
                task.getJavaLauncher().set(toolchains.launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(21))));
                // Its own directory: its mods/ holds Java 21 jars, the Java 8 runs' mods/ downgraded ones.
                task.setWorkingDir(project.file("run/lwjgl3ify"));
            });
        }
        PlatformSetup.installModsBeforeRuns(project, List.of(RUN_CLIENT_21, RUN_SERVER_21),
            project.getConfigurations().getByName(PlatformSetup.DEV_RUN_MODS_21), null);
    }

    /** Found via the FMLCorePlugin manifest entry in production and -Dfml.coreMods.load in dev. */
    static void coremod(Project project, String className) {
        project.getTasks().named("jar", Jar.class, task -> task.getManifest().attributes(Map.of(
            "FMLCorePlugin", className,
            "FMLCorePluginContainsFMLMod", "true",
            "ForceLoadAsMod", "true")));
        minecraft(project).getExtraRunJvmArguments().add("-Dfml.coreMods.load=" + className);
    }

    private static MinecraftExtension minecraft(Project project) {
        return project.getExtensions().getByType(MinecraftExtension.class);
    }
}
