package org.yonside.compat.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logging;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerateModMetadataTest {

    @TempDir
    Path dir;

    private List<String> generate(MinecraftTarget target, String license) throws IOException {
        Path out = Files.createDirectories(dir.resolve("out"));
        PluginFiles.clear(out);
        ModSpec spec = new ModSpec("fixturemod", "Fixture", "", license, "1.2.3", List.of(), List.of());
        GenerateModMetadata.generate(out, target, spec, List.of(dir.resolve("resources").toFile(), dir.resolve("missing").toFile()),
            Logging.getLogger(getClass()));
        try (Stream<Path> files = Files.walk(out)) {
            return files.filter(Files::isRegularFile).map(p -> out.relativize(p).toString().replace('\\', '/')).sorted().toList();
        }
    }

    private void handwrite(String path) throws IOException {
        Path file = dir.resolve("resources").resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "hand-written");
    }

    @Test
    void generatesEveryMetadataFileByDefault() throws IOException {
        assertEquals(List.of("mcmod.info"), generate(MinecraftTarget.V1_7_10, null));
        assertEquals(List.of("META-INF/mods.toml", "pack.mcmeta"), generate(MinecraftTarget.V1_16_5, "MIT"));
        assertTrue(Files.readString(dir.resolve("out/META-INF/mods.toml")).contains("modId=\"fixturemod\""));
    }

    @Test
    void leavesHandWrittenFilesAlone() throws IOException {
        handwrite("mcmod.info");
        assertEquals(List.of(), generate(MinecraftTarget.V1_12_2, null));
    }

    @Test
    void bowsOutPerFile() throws IOException {
        handwrite("META-INF/mods.toml");
        // No license needed: the hand-written mods.toml is the one that would carry it.
        assertEquals(List.of("pack.mcmeta"), generate(MinecraftTarget.V1_16_5, null));
    }

    @Test
    void generatedModsTomlNeedsALicense() {
        assertTrue(assertThrows(GradleException.class, () -> generate(MinecraftTarget.V1_16_5, null))
            .getMessage().contains("compat.mod.license is required"));
    }
}
