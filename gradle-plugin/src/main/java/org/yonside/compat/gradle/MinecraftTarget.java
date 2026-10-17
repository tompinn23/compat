package org.yonside.compat.gradle;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Every Minecraft version a {@code compat { minecraft(...) }} project can build for, and the facts that
 * differ between them: which toolchain sets up Minecraft and Forge, which loader reads the jar, and the
 * defaults for mappings, Forge and Mixin. Adding a version means adding a constant here, and teaching
 * {@link ModSourceGenerator} its lifecycle events if the loader is new.
 */
public enum MinecraftTarget {

    V1_7_10("1.7.10", Toolchain.RFG, Loader.CPW, null, null, null, 0,
        null, null, null,
        new MixinProvider("unimixins", "io.github.legacymoddingmc:unimixins:0.3.1:dev",
            "https://nexus.gtnewhorizons.com/repository/public/", true,
            List.of("org.ow2.asm:asm-debug-all:5.0.4", "com.google.guava:guava:24.1.1-jre", "com.google.code.gson:gson:2.13.2"))),
    V1_12_2("1.12.2", Toolchain.RFG, Loader.FML, null, null, null, 0,
        "stable", "39", null,
        new MixinProvider("mixinbooter", "zone.rong:mixinbooter:11.18",
            "https://maven.cleanroommc.com", false,
            List.of("org.ow2.asm:asm-debug-all:5.2", "com.google.guava:guava:32.1.2-jre", "com.google.code.gson:gson:2.8.9"))),
    V1_16_5("1.16.5", Toolchain.LOOM, Loader.FORGE, "[36,)", "[36.2,)", "[1.16.5,1.17)", 6,
        null, null, "1.16.5-36.2.39",
        // Forge 1.16.5 ships Mixin itself.
        null);

    /** The Gradle plugin that sets up Minecraft and Forge in the version's project. */
    public enum Toolchain {
        /** RetroFuturaGradle: MCP names in dev, reobfuscated to SRG for release. */
        RFG,
        /** Architectury Loom in Forge mode: Mojang names in dev, remapped to SRG for release. */
        LOOM
    }

    /** Which {@code @Mod} annotation the version scans for, and which metadata file it reads. */
    public enum Loader {
        /** {@code cpw.mods.fml.common.Mod}, metadata in mcmod.info. */
        CPW,
        /** {@code net.minecraftforge.fml.common.Mod} with a {@code modid} element, metadata in mcmod.info. */
        FML,
        /** {@code net.minecraftforge.fml.common.Mod} with a {@code value} element, metadata in META-INF/mods.toml. */
        FORGE;

        public boolean usesMcmodInfo() {
            return this != FORGE;
        }

        /** The descriptor of this loader's {@code @Mod} annotation, to spot a hand-written mod class. */
        public String modAnnotation() {
            return this == CPW ? "Lcpw/mods/fml/common/Mod;" : "Lnet/minecraftforge/fml/common/Mod;";
        }
    }

    /**
     * The library that provides Mixin on a version whose Forge does not.
     *
     * @param modId           its mod id, added to the metadata's required mods when mixins are enabled
     * @param dependency      the dev artifact, compiled against and run in dev
     * @param repository      where it is published
     * @param transitive      whether its own dependencies are wanted too
     * @param processorExtras what the Mixin annotation processor needs beside it on that version
     */
    public record MixinProvider(String modId, String dependency, String repository, boolean transitive,
                                List<String> processorExtras) {}

    public final String version;
    /** Task, configuration and publication prefix, matching Compat's stubs project names: mc1710. */
    public final String id;
    public final Toolchain toolchain;
    public final Loader loader;
    /** mods.toml only: the javafml loaderVersion and the default forge / minecraft dependency ranges. */
    public final String loaderVersion;
    public final String forgeRange;
    public final String minecraftRange;
    public final int packFormat;
    /** RFG only, when its default for the version is not wanted. */
    public final String mappingChannel;
    public final String mappingVersion;
    /** Loom only: RFG fixes the Forge version per Minecraft version. */
    public final String forgeVersion;
    /** Null where Forge provides Mixin. */
    public final MixinProvider mixinProvider;

    MinecraftTarget(String version, Toolchain toolchain, Loader loader, String loaderVersion, String forgeRange,
                    String minecraftRange, int packFormat, String mappingChannel, String mappingVersion,
                    String forgeVersion, MixinProvider mixinProvider) {
        this.version = version;
        this.id = "mc" + version.replace(".", "");
        this.toolchain = toolchain;
        this.loader = loader;
        this.loaderVersion = loaderVersion;
        this.forgeRange = forgeRange;
        this.minecraftRange = minecraftRange;
        this.packFormat = packFormat;
        this.mappingChannel = mappingChannel;
        this.mappingVersion = mappingVersion;
        this.forgeVersion = forgeVersion;
        this.mixinProvider = mixinProvider;
    }

    /** The metadata files this version reads, by path inside the jar. */
    public List<String> metadataFiles() {
        return loader.usesMcmodInfo() ? List.of("mcmod.info") : List.of("META-INF/mods.toml", "pack.mcmeta");
    }

    public static MinecraftTarget of(String version) {
        for (MinecraftTarget target : values()) {
            if (target.version.equals(version)) {
                return target;
            }
        }
        throw new IllegalArgumentException("Unsupported Minecraft version '" + version + "'. Supported: "
            + Arrays.stream(values()).map(t -> t.version).collect(Collectors.joining(", ")));
    }
}
