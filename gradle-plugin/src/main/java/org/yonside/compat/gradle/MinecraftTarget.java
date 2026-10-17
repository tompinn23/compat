package org.yonside.compat.gradle;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Every Minecraft version a {@code compat { minecraft(...) }} project can build for, and the facts that
 * differ between them: which toolchain sets up Minecraft and Forge, which loader reads the jar, and the
 * defaults for mappings and Forge. Adding a version means adding a constant here, and teaching
 * {@link ModSourceGenerator} its lifecycle events if the loader is new.
 */
public enum MinecraftTarget {

    V1_7_10("1.7.10", Toolchain.RFG, Loader.CPW, null, null, null, 0, null, null, null),
    V1_12_2("1.12.2", Toolchain.RFG, Loader.FML, null, null, null, 0, "stable", "39", null),
    V1_16_5("1.16.5", Toolchain.LOOM, Loader.FORGE, "[36,)", "[36.2,)", "[1.16.5,1.17)", 6, null, null, "1.16.5-36.2.39");

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

    MinecraftTarget(String version, Toolchain toolchain, Loader loader, String loaderVersion, String forgeRange,
                    String minecraftRange, int packFormat, String mappingChannel, String mappingVersion,
                    String forgeVersion) {
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
