package org.yonside.compat.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/** {@code compat { common() }}: see {@link CompatPlugin}. */
final class CommonSetup {

    /** The consumable configuration version projects embed and compile against. */
    static final String COMMON_ELEMENTS = "compatCommon";

    private CommonSetup() {}

    static void apply(Project project, CompatExtension ext) {
        project.getPluginManager().apply(MavenPublishPlugin.class);

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(21));
        java.withSourcesJar();

        ModInfo mod = ext.getMod();
        project.getExtensions().getByType(BasePluginExtension.class).getArchivesName()
            .set(mod.getModId().map(modId -> modId + "-common").orElse(project.getName()));

        // Selected by name, so Gradle's JVM-version matching never rejects it from a Java 8 project. Carries
        // api and compileOnlyApi dependencies (e.g. Compat Core's API, for an addon) to the version projects'
        // compile classpath; they embed only this project's own jar.
        Configuration elements = project.getConfigurations().create(COMMON_ELEMENTS, c -> {
            c.setCanBeConsumed(true);
            c.setCanBeResolved(false);
            c.setDescription("This common project's Java 21 jar, for the mod's version projects.");
            // Gradle 9 requires consumable configurations to differ from archives and each other by attributes.
            c.getAttributes().attribute(CompatPlugin.VARIANT, COMMON_ELEMENTS);
            c.extendsFrom(project.getConfigurations().getByName(JavaPlugin.API_CONFIGURATION_NAME),
                project.getConfigurations().getByName(JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME));
        });
        project.getArtifacts().add(elements.getName(), project.getTasks().named(JavaPlugin.JAR_TASK_NAME));

        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        MavenPublication publication = publishing.getPublications().create(PlatformSetup.PUBLICATION, MavenPublication.class, pub -> {
            // Artifacts only: the POM never lists Minecraft, Forge or other mods as Maven dependencies.
            pub.artifact(project.getTasks().named(JavaPlugin.JAR_TASK_NAME));
            pub.artifact(project.getTasks().named("sourcesJar", Jar.class));
            PlatformSetup.describe(pub, mod, " (common code)");
        });
        // artifactId is a plain String, and the mod id is set after the plugin is applied.
        project.afterEvaluate(p -> publication.setArtifactId(mod.getModId().get() + "-common"));
    }
}
