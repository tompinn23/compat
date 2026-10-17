package org.yonside.compat.gradle;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.task.RemapJarTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar;

/** Architectury Loom in Forge mode (1.16.5) for {@link PlatformSetup}. */
final class LoomSetup {

    static final String PLUGIN_ID = "dev.architectury.loom";

    private LoomSetup() {}

    static void applyToolchain(Project project, CompatExtension ext) {
        MinecraftTarget target = ext.getTarget();
        // Loom picks its platform from this project property when applied.
        project.getExtensions().getExtraProperties().set("loom.platform", "forge");
        project.getPluginManager().apply(PLUGIN_ID);
        LoomGradleExtensionAPI loom = loom(project);
        String platform = String.valueOf(loom.getPlatform().get());
        if (!platform.equalsIgnoreCase("forge")) {
            throw new GradleException("Architectury Loom set up " + project + " for " + platform + ", not Forge."
                + " Add loom.platform=forge to " + project.file("gradle.properties") + ".");
        }

        project.getExtensions().getByType(JavaPluginExtension.class).getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(8));
        loom.silentMojangMappingsLicense();
        project.getDependencies().add("minecraft", "com.mojang:minecraft:" + target.version);
        project.getDependencies().add("mappings", loom.officialMojangMappings());
        project.getDependencies().add("forge", "net.minecraftforge:forge:" + target.forgeVersion);

        // Forge's mod loader reads the Specification-* entries for the mod's own version in production.
        ModInfo mod = ext.getMod();
        project.afterEvaluate(p -> p.getTasks().named("jar", Jar.class, task -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("Specification-Title", mod.getModId().get());
            attributes.put("Specification-Version", "1");
            attributes.put("Implementation-Title", mod.getDisplayName().getOrElse(mod.getModId().get()));
            attributes.put("Implementation-Version", mod.getVersion().get());
            task.getManifest().attributes(attributes);
        }));
    }

    static PlatformSetup.ReleaseJars configureJars(Project project, CompatExtension ext, TaskProvider<Jar> jar,
                                                   TaskProvider<Jar> linkedJar, TaskProvider<ShadeJar> shade,
                                                   Configuration embed) {
        configureDevRuns(project, ext.getMod().getModId().get(), shade, embed);

        TaskProvider<RemapJarTask> remapJar = project.getTasks().named("remapJar", RemapJarTask.class, task -> {
            task.getArchiveClassifier().set("");
            task.dependsOn(shade);
            task.getInputFile().set(shade.flatMap(Jar::getArchiveFile));
        });
        TaskProvider<RemapJarTask> remapJava21 = project.getTasks().register("remapJava21Jar", RemapJarTask.class, task -> {
            task.dependsOn(linkedJar);
            task.getInputFile().set(linkedJar.flatMap(Jar::getArchiveFile));
            task.getArchiveClassifier().set("java21");
        });
        return new PlatformSetup.ReleaseJars(remapJar, remapJava21);
    }

    /**
     * Dev runs use Java 8, so they must load the downgraded classes. The downgraded jar is unpacked into
     * the layout Forge's userdev expects of a mod -- a resources directory (mods.toml) and
     * a classes directory -- and every run's source set and the mod's files are pointed at it.
     */
    private static void configureDevRuns(Project project, String modId, TaskProvider<ShadeJar> shade, Configuration embed) {
        Provider<Directory> devRunDir = project.getLayout().getBuildDirectory().dir("devRun");
        TaskProvider<Sync> unpack = project.getTasks().register("unpackDevRunJar", Sync.class, task -> {
            task.setDescription("Unpacks the downgraded mod for Java 8 dev runs.");
            Provider<FileTree> contents = shade.flatMap(Jar::getArchiveFile).map(project::zipTree);
            task.into(devRunDir);
            task.from(contents, spec -> {
                spec.include("**/*.class");
                spec.into("classes");
            });
            task.from(contents, spec -> {
                spec.exclude("**/*.class");
                spec.into("resources");
            });
        });
        ConfigurableFileCollection devRunOutput = project.files(devRunDir.map(d -> d.dir("resources")), devRunDir.map(d -> d.dir("classes")));
        devRunOutput.builtBy(unpack);

        // No sources of its own: the downgraded mod, plus main's runtime dependencies (Minecraft, Forge)
        // without main's Java 21 output or the Java 21 common jar embedded in it.
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet devRun = sourceSets.create("devRun", set -> {
            FileCollection java21 = project.files(main.getOutput(), embed);
            set.setRuntimeClasspath(devRunOutput.plus(main.getRuntimeClasspath().filter(file -> !java21.contains(file))));
        });

        LoomGradleExtensionAPI loom = loom(project);
        // The mod and its embedded common code as one group, as in the shipped jar -- but downgraded.
        loom.getMods().create(modId, settings -> settings.getModFiles().from(devRunOutput));
        loom.getRuns().configureEach(run -> run.source(devRun));

        // Forge's userdev finds mod files through MOD_CLASSES: modId%%dir entries, resources first, joined
        // with the path separator. Loom leaves it unset for this setup, so name the downgraded directories.
        project.getTasks().withType(JavaExec.class).matching(t -> t.getName().equals("runClient") || t.getName().equals("runServer"))
            .configureEach(task -> {
                task.dependsOn(unpack);
                task.doFirst(t -> {
                    String modClasses = devRunOutput.getFiles().stream().map(dir -> modId + "%%" + dir)
                        .collect(Collectors.joining(File.pathSeparator));
                    ((JavaExec) t).environment("MOD_CLASSES", modClasses);
                    t.getLogger().lifecycle("{}: MOD_CLASSES={}", t.getName(), modClasses);
                });
            });
    }

    private static LoomGradleExtensionAPI loom(Project project) {
        return project.getExtensions().getByType(LoomGradleExtensionAPI.class);
    }
}
