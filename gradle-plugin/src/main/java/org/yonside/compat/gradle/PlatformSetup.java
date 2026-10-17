package org.yonside.compat.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import kotlin.jvm.functions.Function1;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
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
 *   → jar                       mymod-1.7.10-&lt;v&gt;-unlinked.jar  still calls Compat's API
 *   → linkCompat (+ compatStubs, Compat's 1.7.10 stubs): API types become Minecraft's, API calls become
 *     stub calls, and the stubs and API classes used are copied in under compat.linkPath
 *   → compatDevJar              mymod-1.7.10-&lt;v&gt;-dev.jar       dev names, Java 21, self-contained
 *       ├→ reobf / remap        mymod-1.7.10-&lt;v&gt;-java21.jar    production names, Java 21
 *       └→ downgradeJar → shadeDowngradedApi                  dev names, Java 8: dev runs
 *              └→ reobf / remap  mymod-1.7.10-&lt;v&gt;.jar      production names, Java 8: what players install
 * </pre>
 */
final class PlatformSetup {

    static final String PUBLICATION = "compat";
    /** Resolvable: other version projects' mods to install in this project's dev runs. */
    static final String DEV_RUN_MODS = "compatDevRunMods";
    /** Resolvable: other version projects' Java 21 dev jars, for this project's modern-Java dev runs. */
    static final String DEV_RUN_MODS_21 = "compatDevRunMods21";
    /** Consumable: this project's downgraded dev-name jar, for other projects' dev runs. */
    static final String DEV_MOD_ELEMENTS = "compatDevMod";
    /** Consumable: this project's Java 21 dev-name jar, for other mods in the build to compile against. */
    static final String DEV_ELEMENTS = "compatDev";
    /** Consumable: the Java 8 release jar. */
    static final String RELEASE_ELEMENTS = "compatRelease";
    static final String EMBED = "compatEmbed";
    /** Resolvable, in mod projects: the version's stub jar to link against. */
    static final String STUBS = "compatStubs";
    /** Consumable, in stubs projects: the stub jar. */
    static final String STUB_ELEMENTS = "compatStubElements";
    /** The linked Java 21 dev-name jar task. */
    static final String DEV_JAR = "compatDevJar";

    /** The release jars a toolchain produces from the dev jars. */
    record ReleaseJars(TaskProvider<? extends AbstractArchiveTask> release, TaskProvider<? extends AbstractArchiveTask> java21) {}

    private PlatformSetup() {}

    /** What both kinds of version project share: the toolchain, Java 21 and the embedded common jar. */
    private record Base(SourceSet main, Configuration embed, JavaToolchainService toolchains, TaskProvider<Jar> jar) {}

    private static Base applyBase(Project project, CompatExtension ext, Project common) {
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

        // The common project: embedded in the jar, and on the compile classpath with whatever it exposes as api.
        Map<String, String> commonElements = Map.of("path", common.getPath(), "configuration", CommonSetup.COMMON_ELEMENTS);
        Configuration embed = project.getConfigurations().create(EMBED, c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.setTransitive(false);
            c.setDescription("The common project's jar, embedded in this project's jar.");
        });
        project.getDependencies().add(EMBED, project.getDependencies().project(commonElements));
        // Compile-only: at runtime the common classes are in this project's own jar, and for a mod its
        // API dependencies (Compat's API) have been linked away, so neither belongs on run classpaths.
        project.getDependencies().add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, project.getDependencies().project(commonElements));

        TaskProvider<Jar> jar = project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class, task -> {
            task.dependsOn(embed);
            task.from(embed.getElements().map(jars -> jars.stream().map(project::zipTree).collect(Collectors.toList())), copySpec ->
                copySpec.exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA"));
            task.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
        });
        return new Base(main, embed, toolchains, jar);
    }

    /**
     * {@code compat { stubs("x") }}: the API implemented against one Minecraft version, published as one
     * dev-name Java 21 jar (with the API embedded) that mods are linked against. Not a mod: nothing here
     * is downgraded, remapped or run.
     */
    static void applyStubs(Project project, CompatExtension ext, Project common) {
        Base base = applyBase(project, ext, common);
        MinecraftTarget target = ext.getTarget();
        base.jar().configure(task -> task.getArchiveClassifier().set(""));
        // RFG and Loom still build their production-name copy; keep it out of the way.
        project.getTasks().matching(t -> t.getName().equals("reobfJar") || t.getName().equals("remapJar"))
            .configureEach(t -> ((AbstractArchiveTask) t).getArchiveClassifier().set("srg"));

        Configuration elements = project.getConfigurations().create(STUB_ELEMENTS, c -> {
            c.setCanBeConsumed(true);
            c.setCanBeResolved(false);
            c.setDescription("Compat's Minecraft " + target.version + " stubs with the API, for linking mods.");
            stubAttributes(project, c);
        });
        project.getArtifacts().add(elements.getName(), base.jar());

        ModInfo mod = ext.getMod();
        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        MavenPublication publication = publishing.getPublications().create(PUBLICATION, MavenPublication.class, pub -> {
            pub.artifact(base.jar());
            pub.artifact(project.getTasks().named("sourcesJar", Jar.class));
            describe(pub, mod, " stubs for Minecraft " + target.version);
        });
        project.afterEvaluate(p -> publication.setArtifactId(mod.getModId().get() + "-" + target.version));
    }

    /** Selects a stubs project's {@link #STUB_ELEMENTS}, or a published stub jar's runtime variant. */
    private static void stubAttributes(Project project, Configuration configuration) {
        configuration.getAttributes().attribute(CompatPlugin.VARIANT, STUB_ELEMENTS);
        configuration.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
    }

    /**
     * {@code compat { minecraft("x") }}: a mod for one Minecraft version. See the class comment for its
     * jars.
     */
    static void apply(Project project, CompatExtension ext, Project common) {
        Base base = applyBase(project, ext, common);
        MinecraftTarget target = ext.getTarget();
        SourceSet main = base.main();
        Configuration embed = base.embed();
        JavaToolchainService toolchains = base.toolchains();
        ModInfo mod = ext.getMod();

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

        // The unlinked jar: the mod's classes, its common jar and the generated @Mod class, all still
        // calling Compat's API. Linking it against the version's stubs gives the dev jar.
        TaskProvider<Jar> jar = base.jar();
        jar.configure(task -> {
            task.getArchiveClassifier().set("unlinked");
            task.from(compileGenerated.flatMap(JavaCompile::getDestinationDirectory));
        });

        Configuration stubs = project.getConfigurations().create(STUBS, c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.setTransitive(false);
            c.setDescription("Compat's Minecraft " + target.version + " stubs, which this mod is linked against.");
            stubAttributes(project, c);
            c.defaultDependencies(deps -> deps.add(project.getDependencies().create(
                CompatPlugin.GROUP + ":" + ModSpec.COMPAT_CORE + "-" + target.version + ":" + CompatPlugin.version())));
        });
        TaskProvider<LinkCompatClasses> link = project.getTasks().register("linkCompat", LinkCompatClasses.class, task -> {
            task.setDescription("Links the mod against Compat's Minecraft " + target.version + " stubs.");
            task.getInput().set(jar.flatMap(Jar::getArchiveFile));
            task.getStubs().from(stubs);
            task.getRelocationBase().set(ext.getLinkPath());
            task.getMinecraft().set(target.version);
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("classes/compat/linked"));
        });
        // The dev jar: linked classes plus everything else the unlinked jar holds, and its manifest.
        TaskProvider<Jar> devJar = project.getTasks().register(DEV_JAR, Jar.class, task -> {
            task.setDescription("Assembles the linked Java 21 dev-name jar.");
            task.getArchiveClassifier().set("dev");
            task.from(link.flatMap(LinkCompatClasses::getOutputDirectory));
            task.from(jar.flatMap(Jar::getArchiveFile).map(project::zipTree), copySpec ->
                copySpec.exclude("**/*.class", "META-INF/MANIFEST.MF"));
            task.doFirst(t -> task.getManifest().attributes(jar.get().getManifest().getAttributes()));
        });

        // Java 8 jars in dev names, still referencing JvmDowngrader's stubs, then with them bundled.
        project.getTasks().named("downgradeJar", DowngradeJar.class, task -> {
            task.getInputFile().set(devJar.flatMap(Jar::getArchiveFile));
            task.getArchiveClassifier().set("dev-downgraded");
        });
        Provider<String> shadePath = ext.getShadePath();
        TaskProvider<ShadeJar> shade = project.getTasks().named("shadeDowngradedApi", ShadeJar.class, task -> {
            task.getArchiveClassifier().set("dev-shaded");
            task.getShadePath().set(project.provider(() -> {
                String path = shadePath.get();
                return (Function1<String, String>) jarName -> path;
            }));
        });

        ReleaseJars release = switch (target.toolchain) {
            case RFG -> RfgSetup.configureJars(project, ext, jar, devJar, shade, embed);
            case LOOM -> LoomSetup.configureJars(project, ext, jar, devJar, shade, embed);
        };
        project.getTasks().named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).configure(task -> task.dependsOn(release.release(), release.java21()));

        consumable(project, DEV_ELEMENTS, devJar);
        consumable(project, DEV_MOD_ELEMENTS, shade);
        consumable(project, RELEASE_ELEMENTS, release.release());
        configureDevRuns(project);

        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        MavenPublication publication = publishing.getPublications().create(PUBLICATION, MavenPublication.class, pub -> {
            // Artifacts only: the POM never lists Minecraft, Forge or other mods as Maven dependencies.
            pub.artifact(release.release());                              // Java 8, what players install
            pub.artifact(release.java21());                               // -java21
            pub.artifact(devJar);                                         // -dev, linked: what other mods compile against
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
     * server smoke test does). The Java 8 runs get other mods' downgraded jars; modern-Java runs such as
     * lwjgl3ify's get their Java 21 dev jars ({@link #DEV_RUN_MODS_21}).
     */
    private static void configureDevRuns(Project project) {
        Configuration mods = devRunModsConfiguration(project, DEV_RUN_MODS, "Java 8 dev runs");
        // Filled by compat.devRunMod; compat.lwjgl3ify installs it in its runs.
        devRunModsConfiguration(project, DEV_RUN_MODS_21, "Java 21 dev runs (lwjgl3ify)");
        boolean loom = project.getPluginManager().hasPlugin("dev.architectury.loom");
        // Loom only sets a run task's working directory when it launches, so use its default run directory.
        File loomRunDir = loom ? project.getLayout().getProjectDirectory().dir("run").getAsFile() : null;
        installModsBeforeRuns(project, List.of("runClient", "runServer"), mods, loomRunDir);
    }

    private static Configuration devRunModsConfiguration(Project project, String name, String purpose) {
        return project.getConfigurations().create(name, c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.setTransitive(false);
            c.setDescription("Mods installed in this project's " + purpose + ", e.g. via compat.devRunMod(\":other:mc1710\").");
        });
    }

    /**
     * A prepare task per run, rather than a doFirst on the run: RFG checks the EULA in an action of its
     * own, which would otherwise come first on runs it sets up later (runServer21).
     */
    static void installModsBeforeRuns(Project project, List<String> runs, Configuration mods, File fixedRunDir) {
        boolean acceptEula = project.getProviders().gradleProperty("compat.acceptEula").map(Boolean::parseBoolean).getOrElse(false);
        for (String name : runs) {
            boolean writeEula = name.startsWith("runServer") && acceptEula;
            TaskProvider<Task> install = project.getTasks().register("prepare" + Character.toUpperCase(name.charAt(0)) + name.substring(1), task -> {
                task.setDescription("Installs the dev-run mods in " + name + "'s mods folder.");
                task.dependsOn(mods);
                FileCollection modFiles = mods;
                task.doLast(t -> {
                    File dir = fixedRunDir != null ? fixedRunDir : project.getTasks().named(name, JavaExec.class).get().getWorkingDir();
                    if (writeEula) {
                        dir.mkdirs();
                        write(new File(dir, "eula.txt"), "eula=true\n");
                    }
                    File modsDir = new File(dir, "mods");
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
                    t.getLogger().lifecycle("{}: installed dev-run mods {} in {}", name,
                        modFiles.getFiles().stream().map(File::getName).toList(), modsDir);
                });
            });
            project.getTasks().withType(JavaExec.class).matching(t -> t.getName().equals(name)).configureEach(run -> run.dependsOn(install));
        }
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
