package org.yonside.compat.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import kotlin.jvm.functions.Function1;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.jvm.tasks.ProcessResources;
import xyz.wagyourtail.jvmdg.gradle.JVMDowngraderPlugin;
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar;
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar;

/**
 * {@code compat { minecraft(...) }}: what every version project gets whichever toolchain it uses.
 * {@link RfgSetup} and {@link LoomSetup} add the toolchain itself, the release jars and dev runs.
 *
 * <p>The jars, for e.g. 1.7.10 and mod id {@code mymod}:
 * <pre>
 * compileJava (21) + compatEmbed (common jar) + compileCompatModJava (generated @Mod, 21)
 *   → jar                     mymod-1.7.10-&lt;v&gt;-dev.jar       dev names, Java 21: what other mods compile against
 *       ├→ reobf / remap      mymod-1.7.10-&lt;v&gt;-java21.jar    production names, Java 21
 *       └→ downgradeJar → shadeDowngradedApi                  dev names, Java 8: dev runs
 *              └→ reobf / remap  mymod-1.7.10-&lt;v&gt;.jar      production names, Java 8: what players install
 * </pre>
 */
final class PlatformSetup {

    static final String PUBLICATION = "compat";
    /** Resolvable: other version projects' mods to install in this project's dev runs. */
    static final String DEV_RUN_MODS = "compatDevRunMods";
    /** Consumable: this project's downgraded dev-name jar, for other projects' dev runs. */
    static final String DEV_MOD_ELEMENTS = "compatDevMod";
    /** Consumable: this project's Java 21 dev-name jar, for other mods in the build to compile against. */
    static final String DEV_ELEMENTS = "compatDev";
    /** Consumable: the Java 8 release jar. */
    static final String RELEASE_ELEMENTS = "compatRelease";
    static final String EMBED = "compatEmbed";

    /** The release jars a toolchain produces from the dev jars. */
    record ReleaseJars(TaskProvider<? extends AbstractArchiveTask> release, TaskProvider<? extends AbstractArchiveTask> java21) {}

    private PlatformSetup() {}

    static void apply(Project project, CompatExtension ext, Project common) {
        MinecraftTarget target = ext.getTarget();
        switch (target.toolchain) {
            case RFG -> RfgSetup.applyToolchain(project, ext);
            case LOOM -> LoomSetup.applyToolchain(project, ext);
        }
        // After RFG / Loom, which apply the java plugin: JvmDowngrader's tasks start from the jar task.
        project.getPluginManager().apply(JVMDowngraderPlugin.class);
        project.getPluginManager().apply(MavenPublishPlugin.class);

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.withSourcesJar();
        SourceSet main = project.getExtensions().getByType(SourceSetContainer.class).getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        ModInfo mod = ext.getMod();
        project.getExtensions().getByType(BasePluginExtension.class).getArchivesName()
            .set(mod.getModId().map(modId -> modId + "-" + target.version));

        // The project's toolchain stays at Java 8 for the Minecraft tooling and dev runs; only the mod's
        // own code compiles at 21.
        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        project.getTasks().named(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaCompile.class, task -> java21(task, toolchains));

        // The common project: embedded in the jar, and on the classpath with whatever it exposes as api.
        Map<String, String> commonElements = Map.of("path", common.getPath(), "configuration", CommonSetup.COMMON_ELEMENTS);
        Configuration embed = project.getConfigurations().create(EMBED, c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.setTransitive(false);
            c.setDescription("The common project's jar, embedded in this project's jar.");
        });
        project.getDependencies().add(EMBED, project.getDependencies().project(commonElements));
        project.getDependencies().add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, project.getDependencies().project(commonElements));

        // Generated @Mod sources (only if the author wrote none) and metadata (each file only if absent).
        Provider<ModSpec> spec = project.provider(() -> ModSpec.of(mod, ext.inheritedMod()));
        TaskProvider<GenerateModMetadata> metadata = project.getTasks().register("generateCompatMetadata", GenerateModMetadata.class, task -> {
            task.setDescription("Generates the Minecraft " + target.version + " metadata files not hand-written in resources.");
            task.getResourceDirectories().from(project.provider(() -> main.getResources().getSrcDirs()));
            task.getMinecraft().set(target.version);
            task.getSpec().set(spec);
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("generated/compat/resources"));
        });
        project.getTasks().named(main.getProcessResourcesTaskName(), ProcessResources.class,
            task -> task.from(metadata));

        TaskProvider<GenerateModSources> sources = project.getTasks().register("generateCompatMod", GenerateModSources.class, task -> {
            task.setDescription("Generates the Minecraft " + target.version + " @Mod class for the @CompatMod class, unless one is hand-written.");
            task.getClasses().from(main.getOutput().getClassesDirs(), embed);
            task.getMinecraft().set(target.version);
            task.getSpec().set(spec);
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("generated/compat/java"));
        });
        TaskProvider<JavaCompile> compileGenerated = project.getTasks().register("compileCompatModJava", JavaCompile.class, task -> {
            task.setDescription("Compiles the generated Minecraft " + target.version + " @Mod class.");
            task.setSource(sources.flatMap(GenerateModSources::getOutputDirectory));
            task.setClasspath(main.getOutput().getClassesDirs().plus(main.getCompileClasspath()));
            task.getOptions().getCompilerArgs().add("-proc:none");
            task.getOptions().setEncoding("UTF-8");
            task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("classes/compat/java"));
            java21(task, toolchains);
        });

        TaskProvider<Jar> jar = project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class, task -> {
            task.getArchiveClassifier().set("dev");
            task.from(compileGenerated.flatMap(JavaCompile::getDestinationDirectory));
            task.dependsOn(embed);
            task.from(embed.getElements().map(jars -> jars.stream().map(project::zipTree).collect(Collectors.toList())), copySpec ->
                copySpec.exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA"));
            task.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
        });

        // Java 8 jars in dev names, still referencing JvmDowngrader's stubs, then with them bundled.
        project.getTasks().named("downgradeJar", DowngradeJar.class, task -> task.getArchiveClassifier().set("dev-downgraded"));
        Provider<String> shadePath = ext.getShadePath();
        TaskProvider<ShadeJar> shade = project.getTasks().named("shadeDowngradedApi", ShadeJar.class, task -> {
            task.getArchiveClassifier().set("dev-shaded");
            task.getShadePath().set(project.provider(() -> {
                String path = shadePath.get();
                return (Function1<String, String>) jarName -> path;
            }));
        });

        ReleaseJars release = switch (target.toolchain) {
            case RFG -> RfgSetup.configureJars(project, ext, jar, shade, embed);
            case LOOM -> LoomSetup.configureJars(project, ext, jar, shade, embed);
        };
        project.getTasks().named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).configure(task -> task.dependsOn(release.release(), release.java21()));

        consumable(project, DEV_ELEMENTS, jar);
        consumable(project, DEV_MOD_ELEMENTS, shade);
        consumable(project, RELEASE_ELEMENTS, release.release());
        configureDevRuns(project);

        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        MavenPublication publication = publishing.getPublications().create(PUBLICATION, MavenPublication.class, pub -> {
            // Artifacts only: the POM never lists Minecraft, Forge or other mods as Maven dependencies.
            pub.artifact(release.release());                              // Java 8, what players install
            pub.artifact(release.java21());                               // -java21
            pub.artifact(jar);                                            // -dev, what other mods compile against
            pub.artifact(project.getTasks().named("sourcesJar", Jar.class));  // -sources
            describe(pub, mod, " for Minecraft " + target.version);
        });
        project.afterEvaluate(p -> publication.setArtifactId(mod.getModId().get() + "-" + target.version));
    }

    static void enableMixins(Project project, CompatExtension ext, MixinOptions options) {
        MinecraftTarget target = ext.getTarget();
        switch (target.toolchain) {
            case RFG -> RfgSetup.enableMixins(project, target, options);
            case LOOM -> LoomSetup.enableMixins(project, options);
        }
        if (target.mixinProvider != null) {
            ext.getMod().requires(target.mixinProvider.modId());
        }
    }

    static void describe(MavenPublication pub, ModInfo mod, String suffix) {
        pub.pom(pom -> {
            pom.getName().set(mod.getDisplayName().orElse(mod.getModId()).map(name -> name + suffix));
            pom.getDescription().set(mod.getDescription());
            pom.licenses(licenses -> licenses.license(license -> license.getName().set(mod.getLicense())));
        });
    }

    private static void java21(JavaCompile task, JavaToolchainService toolchains) {
        task.getJavaCompiler().set(toolchains.compilerFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(21))));
        task.getOptions().getRelease().set(21);
    }

    private static void consumable(Project project, String name, TaskProvider<? extends Task> artifact) {
        Configuration configuration = project.getConfigurations().create(name, c -> {
            c.setCanBeConsumed(true);
            c.setCanBeResolved(false);
            // Gradle 9 requires consumable configurations to differ from archives and each other by attributes.
            c.getAttributes().attribute(CompatPlugin.VARIANT, name);
        });
        project.getArtifacts().add(configuration.getName(), artifact);
    }

    /**
     * Dev runs install {@link #DEV_RUN_MODS} in their mods folder rather than on the classpath, exactly
     * as a player would, and accept Mojang's EULA only when asked to with -Pcompat.acceptEula=true (CI's
     * server smoke test does).
     */
    private static void configureDevRuns(Project project) {
        Configuration mods = project.getConfigurations().create(DEV_RUN_MODS, c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.setTransitive(false);
            c.setDescription("Mods installed in this project's dev runs, e.g. via compat.devRunMod(\":other:mc1710\").");
        });
        boolean acceptEula = project.getProviders().gradleProperty("compat.acceptEula").map(Boolean::parseBoolean).getOrElse(false);
        boolean loom = project.getPluginManager().hasPlugin("dev.architectury.loom");
        // Loom only sets a run task's working directory when it launches, so use its default run directory.
        File loomRunDir = loom ? project.getLayout().getProjectDirectory().dir("run").getAsFile() : null;
        project.getTasks().withType(JavaExec.class).matching(t -> t.getName().equals("runClient") || t.getName().equals("runServer"))
            .configureEach(task -> {
                task.dependsOn(mods);
                FileCollection modFiles = mods;
                boolean writeEula = task.getName().equals("runServer") && acceptEula;
                task.doFirst(t -> {
                    File runDir = loomRunDir != null ? loomRunDir : ((JavaExec) t).getWorkingDir();
                    if (writeEula) {
                        runDir.mkdirs();
                        write(new File(runDir, "eula.txt"), "eula=true\n");
                    }
                    File modsDir = new File(runDir, "mods");
                    modsDir.mkdirs();
                    // Drop any copy from an earlier version of the same mod so the loader does not see it twice.
                    Set<String> names = modFiles.getFiles().stream().map(f -> baseName(f.getName())).collect(Collectors.toSet());
                    File[] existing = modsDir.listFiles();
                    if (existing != null) {
                        for (File old : existing) {
                            if (names.contains(baseName(old.getName()))) {
                                old.delete();
                            }
                        }
                    }
                    for (File file : modFiles.getFiles()) {
                        copy(file, new File(modsDir, file.getName()));
                    }
                    t.getLogger().lifecycle("{}: installed dev-run mods {} in {}", t.getName(),
                        modFiles.getFiles().stream().map(File::getName).toList(), modsDir);
                });
            });
    }

    /** {@code mymod-1.7.10-0.1.0-dev-shaded.jar} → {@code mymod-1.7.10}: archive name up to the version. */
    private static String baseName(String fileName) {
        String[] parts = fileName.split("-");
        return parts.length >= 2 ? parts[0] + "-" + parts[1] : fileName;
    }

    private static void write(File file, String content) {
        try {
            Files.writeString(file.toPath(), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copy(File from, File to) {
        try {
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** For {@link RfgSetup} and {@link LoomSetup}: files of {@code embed} and the dev jar, to drop from dev-run classpaths. */
    static boolean isJava21Output(File file, Provider<? extends RegularFile> devJar, FileCollection embed) {
        return file.equals(devJar.get().getAsFile()) || embed.contains(file);
    }
}
