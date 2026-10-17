package org.yonside.compat.gradle;

import java.util.List;
import java.util.Map;

import com.gtnewhorizons.retrofuturagradle.MinecraftExtension;
import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar;
import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils;
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
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar;

/** RetroFuturaGradle (1.7.10, 1.12.2) for {@link PlatformSetup}. */
final class RfgSetup {

    static final String PLUGIN_ID = "com.gtnewhorizons.retrofuturagradle";

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
                                                   TaskProvider<ShadeJar> shade, Configuration embed) {
        TaskProvider<ReobfuscatedJar> reobfJar = project.getTasks().named("reobfJar", ReobfuscatedJar.class,
            task -> task.getArchiveClassifier().set("java21"));
        // Created by RFG's reobf<JarTask> rule.
        TaskProvider<ReobfuscatedJar> reobfShaded = project.getTasks().named("reobfShadeDowngradedApi", ReobfuscatedJar.class, task -> {
            task.getArchiveClassifier().set("");
            task.getExtraSrgFiles().from(reobfJar.map(ReobfuscatedJar::getExtraSrgFiles));
            task.getExtraSrgEntries().addAll(reobfJar.flatMap(ReobfuscatedJar::getExtraSrgEntries));
        });

        // Dev runs use Java 8, so swap the Java 21 dev jar and embedded common jar for the downgraded jar.
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
        return new PlatformSetup.ReleaseJars(reobfShaded, reobfJar);
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
