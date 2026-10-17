package org.yonside.compat.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logging;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerateModSourcesTest {

    @TempDir
    Path dir;

    private static final ModSpec SPEC = new ModSpec("fixturemod", "Fixture", "", "MIT", "1.2.3", List.of(),
        List.of(new ModSpec.Dependency("compatcore", "[0.1.0,)", true, "AFTER", "BOTH", Set.of())));

    private static final String ADDON = """
        package com.example;
        import org.yonside.compat.api.*;
        @CompatMod
        public class FixtureMod implements CompatAddon {}
        """;

    private List<String> generate(MinecraftTarget target, File... roots) throws IOException {
        Path out = Files.createDirectories(dir.resolve("out"));
        PluginFiles.clear(out);
        GenerateModSources.generate(out, target, SPEC, List.of(roots), Logging.getLogger(getClass()));
        try (Stream<Path> files = Files.walk(out)) {
            return files.filter(Files::isRegularFile).map(p -> out.relativize(p).toString().replace('\\', '/')).sorted().toList();
        }
    }

    @Test
    void generatesTheModClassForTheCompatModClassInTheCommonJar() throws IOException {
        // The @CompatMod class lives in the common project, so it reaches the version project as a jar.
        Path common = Fixtures.jar(Fixtures.compile(dir, "common", Map.of("com/example/FixtureMod.java", ADDON)), dir.resolve("common.jar"));
        Path own = Files.createDirectories(dir.resolve("own-classes"));

        assertEquals(List.of("com/example/FixtureModForgeEvents.java", "com/example/FixtureModForgeMod.java"),
            generate(MinecraftTarget.V1_7_10, own.toFile(), common.toFile()));
        String mod = Files.readString(dir.resolve("out/com/example/FixtureModForgeMod.java"));
        assertTrue(mod.contains("import cpw.mods.fml.common.Mod;"), mod);
        assertTrue(mod.contains("modid = \"fixturemod\""), mod);
        assertTrue(mod.contains("acceptedMinecraftVersions = \"[1.7.10]\""), mod);
        assertTrue(mod.contains("dependencies = \"required-after:compatcore@[0.1.0,)\""), mod);
        assertTrue(mod.contains("private final CompatAddon addon = new com.example.FixtureMod();"), mod);
        String events = Files.readString(dir.resolve("out/com/example/FixtureModForgeEvents.java"));
        assertTrue(events.contains("addon.onWorldLoad((CompatWorld) event.world);"), events);
        assertTrue(events.contains("import cpw.mods.fml.common.eventhandler.SubscribeEvent;"), events);
    }

    @Test
    void laterVersionsUseTheirOwnLoaderApi() throws IOException {
        Path classes = Fixtures.compile(dir, "classes", Map.of("com/example/FixtureMod.java", ADDON));

        generate(MinecraftTarget.V1_12_2, classes.toFile());
        String events = Files.readString(dir.resolve("out/com/example/FixtureModForgeEvents.java"));
        assertTrue(events.contains("addon.onWorldLoad((CompatWorld) event.getWorld());"), events);
        assertTrue(Files.readString(dir.resolve("out/com/example/FixtureModForgeMod.java")).contains("dependencies = \"required-after:compatcore@[0.1.0,)\""));

        generate(MinecraftTarget.V1_16_5, classes.toFile());
        String mod = Files.readString(dir.resolve("out/com/example/FixtureModForgeMod.java"));
        assertTrue(mod.contains("@Mod(\"fixturemod\")"), mod);
        assertTrue(mod.contains("public void init(FMLCommonSetupEvent event)"), mod);
        assertFalse(mod.contains("dependencies"), "mods.toml carries 1.16.5's dependencies");
    }

    @Test
    void bowsOutForAHandWrittenModClass() throws IOException {
        Path classes = Fixtures.compile(dir, "classes", Map.of(
            "com/example/FixtureMod.java", ADDON,
            "com/example/MyMod.java", "package com.example; @cpw.mods.fml.common.Mod(modid = \"fixturemod\") public class MyMod {}"));
        assertEquals(List.of(), generate(MinecraftTarget.V1_7_10, classes.toFile()));
    }

    @Test
    void onlyThatVersionsModAnnotationCounts() throws IOException {
        // A 1.12.2-style @Mod is not a 1.7.10 mod class.
        Path classes = Fixtures.compile(dir, "classes", Map.of(
            "com/example/FixtureMod.java", ADDON,
            "com/example/Other.java", "package com.example; @net.minecraftforge.fml.common.Mod public class Other {}"));
        assertEquals(2, generate(MinecraftTarget.V1_7_10, classes.toFile()).size());
        assertEquals(List.of(), generate(MinecraftTarget.V1_12_2, classes.toFile()));
    }

    @Test
    void generatesNothingWithoutAnnotatedClass() throws IOException {
        Path classes = Fixtures.compile(dir, "classes", Map.of("com/example/Plain.java", "package com.example; public class Plain {}"));
        assertEquals(List.of(), generate(MinecraftTarget.V1_16_5, classes.toFile()));
    }

    @Test
    void rejectsInvalidCompatModClasses() throws IOException {
        Path two = Fixtures.compile(dir, "two", Map.of("com/example/FixtureMod.java", ADDON,
            "com/example/Second.java", "package com.example; @org.yonside.compat.api.CompatMod public class Second implements org.yonside.compat.api.CompatAddon {}"));
        assertTrue(assertThrows(GradleException.class, () -> generate(MinecraftTarget.V1_7_10, two.toFile()))
            .getMessage().contains("Only one class may be annotated with @CompatMod"));

        Path noCtor = Fixtures.compile(dir, "noctor", Map.of("com/example/FixtureMod.java",
            "package com.example; @org.yonside.compat.api.CompatMod public class FixtureMod implements org.yonside.compat.api.CompatAddon { public FixtureMod(int x) {} }"));
        assertTrue(assertThrows(GradleException.class, () -> generate(MinecraftTarget.V1_7_10, noCtor.toFile()))
            .getMessage().contains("public no-argument constructor"));

        Path notAddon = Fixtures.compile(dir, "notaddon", Map.of("com/example/FixtureMod.java",
            "package com.example; @org.yonside.compat.api.CompatMod public class FixtureMod {}"));
        assertTrue(assertThrows(GradleException.class, () -> generate(MinecraftTarget.V1_7_10, notAddon.toFile()))
            .getMessage().contains("must implement org.yonside.compat.api.CompatAddon"));
    }
}
