package org.yonside.compat.gradle;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.GradleException;

/**
 * Renders a {@link ModSpec} into the metadata one Minecraft version reads. Plain string building: the
 * formats are small and fixed, and this keeps the plugin free of JSON and TOML libraries.
 */
public final class MetadataRenderer {

    private MetadataRenderer() {}

    /** One of {@code target}'s {@link MinecraftTarget#metadataFiles()}, by its path inside the jar. */
    public static String render(ModSpec spec, MinecraftTarget target, String path) {
        return switch (path) {
            case "mcmod.info" -> mcmodInfo(spec, target);
            case "META-INF/mods.toml" -> modsToml(spec, target);
            case "pack.mcmeta" -> packMcmeta(spec, target);
            default -> throw new IllegalArgumentException("Not a metadata file: " + path);
        };
    }

    /**
     * Read through the generated {@code @Mod}'s useMetadata. Its dependency lists are for display only;
     * FML enforces the annotation's dependencies string, see {@link #legacyDependencies}.
     */
    static String mcmodInfo(ModSpec spec, MinecraftTarget target) {
        List<ModSpec.Dependency> deps = spec.dependencies().stream().filter(d -> d.appliesTo(target)).toList();
        List<String> required = deps.stream().filter(ModSpec.Dependency::mandatory).map(MetadataRenderer::withRange).toList();
        List<String> after = deps.stream().filter(d -> d.ordering().equals("AFTER")).map(MetadataRenderer::withRange).toList();
        List<String> before = deps.stream().filter(d -> d.ordering().equals("BEFORE")).map(MetadataRenderer::withRange).toList();

        StringBuilder out = new StringBuilder("[{\n");
        field(out, "modid", json(spec.modId()));
        field(out, "name", json(spec.displayName()));
        field(out, "description", json(spec.description()));
        field(out, "version", json(spec.version()));
        field(out, "mcversion", json(target.version));
        field(out, "authorList", jsonArray(spec.authors()));
        field(out, "requiredMods", jsonArray(required));
        field(out, "dependencies", jsonArray(after));
        out.append("  \"dependants\": ").append(jsonArray(before)).append('\n');
        return out.append("}]\n").toString();
    }

    static String modsToml(ModSpec spec, MinecraftTarget target) {
        if (spec.license() == null) {
            throw new GradleException("compat.mod.license is required: mods.toml does not load without one.");
        }
        StringBuilder out = new StringBuilder();
        out.append("modLoader=\"javafml\"\n");
        out.append("loaderVersion=").append(toml(target.loaderVersion)).append('\n');
        out.append("license=").append(toml(spec.license())).append("\n\n");

        out.append("[[mods]]\n");
        out.append("modId=").append(toml(spec.modId())).append('\n');
        out.append("version=").append(toml(spec.version())).append('\n');
        out.append("displayName=").append(toml(spec.displayName())).append('\n');
        out.append("description=").append(toml(spec.description())).append('\n');
        if (!spec.authors().isEmpty()) {
            out.append("authors=").append(toml(String.join(", ", spec.authors()))).append('\n');
        }

        List<ModSpec.Dependency> deps = new ArrayList<>();
        boolean declaresForge = false;
        boolean declaresMinecraft = false;
        for (ModSpec.Dependency dep : spec.dependencies()) {
            if (dep.appliesTo(target)) {
                deps.add(dep);
                declaresForge |= dep.modId().equals("forge");
                declaresMinecraft |= dep.modId().equals("minecraft");
            }
        }
        if (!declaresForge) {
            deps.add(new ModSpec.Dependency("forge", target.forgeRange, true, "NONE", "BOTH", Set.of()));
        }
        if (!declaresMinecraft) {
            deps.add(new ModSpec.Dependency("minecraft", target.minecraftRange, true, "NONE", "BOTH", Set.of()));
        }
        for (ModSpec.Dependency dep : deps) {
            out.append("\n[[dependencies.").append(spec.modId()).append("]]\n");
            out.append("    modId=").append(toml(dep.modId())).append('\n');
            out.append("    mandatory=").append(dep.mandatory()).append('\n');
            out.append("    versionRange=").append(toml(dep.versionRange() == null ? "[0,)" : dep.versionRange())).append('\n');
            out.append("    ordering=").append(toml(dep.ordering())).append('\n');
            out.append("    side=").append(toml(dep.side())).append('\n');
        }
        return out.toString();
    }

    static String packMcmeta(ModSpec spec, MinecraftTarget target) {
        return "{\n  \"pack\": {\n    \"description\": " + json(spec.displayName() + " resources")
            + ",\n    \"pack_format\": " + target.packFormat + "\n  }\n}\n";
    }

    /**
     * The dependencies element of a generated 1.7.10 / 1.12.2 {@code @Mod}. 1.7.10 only understands the
     * four ordered instructions, so a mandatory dependency with no ordering becomes required-after there.
     */
    public static String legacyDependencies(ModSpec spec, MinecraftTarget target) {
        return spec.dependencies().stream()
            .filter(d -> d.appliesTo(target))
            .map(d -> {
                String instruction = switch (d.ordering()) {
                    case "AFTER" -> d.mandatory() ? "required-after" : "after";
                    case "BEFORE" -> d.mandatory() ? "required-before" : "before";
                    default -> !d.mandatory() ? null
                        : target.loader == MinecraftTarget.Loader.CPW ? "required-after" : "required";
                };
                return instruction == null ? null : instruction + ":" + withRange(d);
            })
            .filter(s -> s != null)
            .collect(Collectors.joining(";"));
    }

    private static String withRange(ModSpec.Dependency dep) {
        return dep.versionRange() == null ? dep.modId() : dep.modId() + "@" + dep.versionRange();
    }

    private static void field(StringBuilder out, String key, String value) {
        out.append("  ").append(json(key)).append(": ").append(value).append(",\n");
    }

    private static String jsonArray(List<String> values) {
        return values.stream().map(MetadataRenderer::json).collect(Collectors.joining(", ", "[", "]"));
    }

    static String json(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /** TOML basic strings use the same escapes as JSON for everything that can appear here. */
    static String toml(String value) {
        return json(value);
    }
}
