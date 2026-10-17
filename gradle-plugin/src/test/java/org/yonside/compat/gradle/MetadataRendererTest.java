package org.yonside.compat.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MetadataRendererTest {

    private static ModSpec spec(List<ModSpec.Dependency> deps) {
        return new ModSpec("examplemod", "Example", "desc", "MIT", "0.1.0", List.of(), deps);
    }

    @Test
    void mcmodInfoOnlyListsThatVersionsDependencies() {
        ModSpec spec = spec(List.of(
            new ModSpec.Dependency("compatcore", null, true, "AFTER", "BOTH", Set.of()),
            new ModSpec.Dependency("other", null, true, "NONE", "BOTH", Set.of(MinecraftTarget.V1_12_2))));
        assertEquals("""
            [{
              "modid": "examplemod",
              "name": "Example",
              "description": "desc",
              "version": "0.1.0",
              "mcversion": "1.7.10",
              "authorList": [],
              "requiredMods": ["compatcore"],
              "dependencies": ["compatcore"],
              "dependants": []
            }]
            """, MetadataRenderer.mcmodInfo(spec, MinecraftTarget.V1_7_10));
    }

    @Test
    void legacyDependencyStringsRespectEachLoadersGrammar() {
        ModSpec spec = spec(List.of(
            new ModSpec.Dependency("a", null, true, "NONE", "BOTH", Set.of(MinecraftTarget.V1_7_10, MinecraftTarget.V1_12_2)),
            new ModSpec.Dependency("b", "[1,)", false, "BEFORE", "BOTH", Set.of(MinecraftTarget.V1_7_10, MinecraftTarget.V1_12_2)),
            new ModSpec.Dependency("c", null, false, "NONE", "BOTH", Set.of(MinecraftTarget.V1_12_2))));
        // 1.7.10 has no bare "required" instruction.
        assertEquals("required-after:a;before:b@[1,)", MetadataRenderer.legacyDependencies(spec, MinecraftTarget.V1_7_10));
        assertEquals("required:a;before:b@[1,)", MetadataRenderer.legacyDependencies(spec, MinecraftTarget.V1_12_2));
    }
}
