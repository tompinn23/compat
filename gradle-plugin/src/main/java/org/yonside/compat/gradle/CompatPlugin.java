package org.yonside.compat.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.plugins.JavaLibraryPlugin;

/**
 * {@code org.yonside.compat}: one plugin for every project of a mod built with Compat, and for Compat
 * itself. Applying it adds {@code java-library} (so build scripts get its accessors whatever the role)
 * and the {@code compat} extension; the project's role, chosen there, sets everything else up:
 *
 * <dl>
 *   <dt>{@code compat { common() }}</dt>
 *   <dd>Java 21, no Minecraft or Forge. Publishes {@code <modId>-common} and exposes its jar to version
 *       projects through the {@code compatCommon} configuration ({@link CommonSetup}).</dd>
 *   <dt>{@code compat { minecraft("1.7.10") }}</dt>
 *   <dd>A mod for that version. RetroFuturaGradle (1.7.10, 1.12.2) or Architectury Loom in Forge mode
 *       (1.16.5) supplies Minecraft and Forge. The project's own sources and its common jar compile as
 *       Java 21; the {@code @Mod} class and metadata are generated unless hand-written; everything is
 *       linked against Compat's stubs for the version, so the jar needs no Compat at runtime;
 *       JvmDowngrader produces the Java 8 release jar; publishes {@code <modId>-<minecraft>}
 *       ({@link PlatformSetup}, {@link Linker}).</dd>
 *   <dt>{@code compat { stubs("1.7.10") }}</dt>
 *   <dd>Compat's own: the API implemented for that version with {@code @StubFor} classes, published as
 *       the jar mods are linked against.</dd>
 * </dl>
 *
 * <p>RetroFuturaGradle, Loom and JvmDowngrader are this plugin's dependencies, so every project of a
 * build shares one copy of each as long as the root project declares this plugin, applied or not. RFG
 * needs that: its RfgCacheService is shared between projects and cannot cross classloaders.
 */
public class CompatPlugin implements Plugin<Project> {

    /** Tells this plugin's consumable configurations apart; consumers select them by name. */
    public static final Attribute<String> VARIANT = Attribute.of("org.yonside.compat.variant", String.class);

    /** Maven group of Compat's own artifacts: the API and each version's stubs. */
    public static final String GROUP = "org.yonside.compat";

    /** This plugin's version, which is also the version of the stubs it links against by default. */
    static String version() {
        Properties properties = new Properties();
        try (InputStream in = CompatPlugin.class.getResourceAsStream("compat-plugin.properties")) {
            properties.load(in);
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("compat-plugin.properties is missing from the compat plugin", e);
        }
        return properties.getProperty("version");
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        project.getExtensions().create(CompatExtension.NAME, CompatExtension.class, project);
    }
}
