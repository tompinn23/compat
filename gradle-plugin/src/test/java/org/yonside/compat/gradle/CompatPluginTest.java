package org.yonside.compat.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The plugin's own logic. Version projects are not built here: they apply RetroFuturaGradle or Loom,
 * which set up real Minecraft. The root build's CI jobs build and boot those.
 */
class CompatPluginTest {

    @TempDir
    Path dir;

    private static CompatExtension compat(Project project) {
        project.getPluginManager().apply(CompatPlugin.class);
        return project.getExtensions().getByType(CompatExtension.class);
    }

    @Test
    void versionProjectsInheritTheCommonModBlock() {
        Project root = ProjectBuilder.builder().withProjectDir(dir.toFile()).build();
        root.setVersion("1.2.3");
        Project commonProject = ProjectBuilder.builder().withName("common").withParent(root).build();
        commonProject.setVersion("1.2.3");
        CompatExtension common = compat(commonProject);
        common.getMod().getModId().set("fixturemod");
        common.getMod().getLicense().set("MIT");
        common.getMod().requires("other", dep -> dep.getVersionRange().set("[1,)"));
        common.getMod().requires("compatcore", dep -> dep.getVersionRange().set("[0.1.0,)"));

        Project versionProject = ProjectBuilder.builder().withName("mc1710").withParent(root).build();
        versionProject.setVersion("1.2.3");
        CompatExtension version = compat(versionProject);
        version.getMod().inheritFrom(common.getMod());
        version.getMod().getDisplayName().set("Fixture for 1.7.10");
        version.getMod().requires("other", dep -> dep.getMandatory().set(false));
        version.getMod().requires("unimixins");

        ModSpec spec = ModSpec.of(version.getMod(), common.getMod());
        assertEquals("fixturemod", spec.modId());
        assertEquals("Fixture for 1.7.10", spec.displayName());
        assertEquals("MIT", spec.license());
        assertEquals("1.2.3", spec.version());
        Map<String, ModSpec.Dependency> deps = spec.dependencies().stream()
            .collect(Collectors.toMap(ModSpec.Dependency::modId, d -> d));
        // Requires iterate by name; the common project's come first.
        assertEquals(List.of("compatcore", "other", "unimixins"), spec.dependencies().stream().map(ModSpec.Dependency::modId).toList());
        // The version project's "other" replaces the common one's; the common project's others carry over.
        assertEquals("[0.1.0,)", deps.get("compatcore").versionRange());
        assertEquals(false, deps.get("other").mandatory());
        assertNull(deps.get("other").versionRange());
    }

    @Test
    void modsDependOnlyOnWhatTheyDeclare() {
        // Compat is linked into each mod, so there is no Compat mod to depend on.
        CompatExtension addon = compat(ProjectBuilder.builder().build());
        addon.getMod().getModId().set("fixturemod");
        assertEquals(List.of(), ModSpec.of(addon.getMod(), null).dependencies());
    }

    @Test
    void commonSetsUpTheEmbeddableJarAndPublication() {
        Project project = ProjectBuilder.builder().build();
        CompatExtension compat = compat(project);
        compat.common();
        compat.getMod().getModId().set("fixturemod");

        assertNotNull(project.getConfigurations().findByName(CommonSetup.COMMON_ELEMENTS));
        assertNotNull(project.getExtensions().getByType(PublishingExtension.class).getPublications()
            .withType(MavenPublication.class).findByName(PlatformSetup.PUBLICATION));
        assertEquals("org/yonside/test/fixturemod/jvmdg/", withGroup(project, compat));
    }

    private static String withGroup(Project project, CompatExtension compat) {
        project.setGroup("org.yonside.test");
        return compat.getShadePath().get();
    }

    @Test
    void rolesAreExclusiveAndValidated() {
        Project root = ProjectBuilder.builder().withProjectDir(dir.toFile()).build();
        CompatExtension common = compat(root);
        common.common();
        assertTrue(assertThrows(GradleException.class, () -> common.minecraft("1.7.10")).getMessage().contains("already a common project"));
        assertTrue(assertThrows(GradleException.class, () -> common.mixins()).getMessage().contains("needs compat.minecraft"));

        CompatExtension other = compat(ProjectBuilder.builder().withName("mc1710").withParent(root).build());
        assertTrue(assertThrows(GradleException.class, () -> other.minecraft("1.99")).getMessage().contains("Unsupported Minecraft version '1.99'"));
        CompatExtension orphan = compat(ProjectBuilder.builder().withName("mc1122").withParent(root).build());
        assertTrue(assertThrows(GradleException.class, () -> orphan.minecraft("1.12.2")).getMessage().contains("no sibling project named 'common'"));
    }

    @Test
    void commonJarIsJava21AndPomListsNoDependencies() throws IOException {
        write("settings.gradle", "rootProject.name = 'fixture'\ninclude 'api', 'common'\n");
        // TestKit has its own Gradle user home, so name CI's JDK 21 here; elsewhere auto-detection finds one.
        write("gradle.properties", "org.gradle.java.installations.fromEnv=JAVA_HOME_21_X64\n");
        write("build.gradle", "subprojects { version = '1.2.3'; group = 'org.example' }\n");
        write("api/build.gradle", "plugins { id 'java-library' }\njava { toolchain { languageVersion = JavaLanguageVersion.of(21) } }\n");
        write("api/src/main/java/org/yonside/compat/api/CompatAddon.java", "package org.yonside.compat.api; public interface CompatAddon {}");
        write("common/build.gradle", """
            plugins { id 'org.yonside.compat' }
            compat {
                common()
                mod { modId = 'fixturemod'; license = 'MIT' }
            }
            dependencies { compileOnlyApi project(':api') }
            """);
        write("common/src/main/java/com/example/FixtureMod.java", """
            package com.example;
            public record FixtureMod(int x) implements org.yonside.compat.api.CompatAddon {}
            """);

        GradleRunner.create().withProjectDir(dir.toFile()).withPluginClasspath()
            .withArguments(":common:jar", ":common:generatePomFileForCompatPublication", "--stacktrace").forwardOutput().build();

        File jar = dir.resolve("common/build/libs/fixturemod-common-1.2.3.jar").toFile();
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry("com/example/FixtureMod.class");
            assertNotNull(entry);
            try (InputStream in = zip.getInputStream(entry)) {
                byte[] header = in.readNBytes(8);
                assertEquals(65, ((header[6] & 0xff) << 8) | (header[7] & 0xff));
            }
            // compileOnlyApi stays out of the jar.
            assertNull(zip.getEntry("org/yonside/compat/api/CompatAddon.class"));
        }
        String pom = Files.readString(dir.resolve("common/build/publications/compat/pom-default.xml"));
        assertTrue(pom.contains("<artifactId>fixturemod-common</artifactId>"), pom);
        assertTrue(pom.contains("<name>MIT</name>"), pom);
        assertTrue(!pom.contains("<dependencies>"), pom);
    }

    private void write(String path, String content) throws IOException {
        Path file = dir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
