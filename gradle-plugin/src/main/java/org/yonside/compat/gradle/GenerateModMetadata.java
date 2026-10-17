package org.yonside.compat.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Generates each of one version's metadata files (mcmod.info, or META-INF/mods.toml and pack.mcmeta)
 * that the project's own resources do not already contain. A hand-written file is left alone entirely.
 */
@CacheableTask
public abstract class GenerateModMetadata extends DefaultTask {

    /** The project's own resource directories, checked for hand-written metadata. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @IgnoreEmptyDirectories
    public abstract ConfigurableFileCollection getResourceDirectories();

    /** A {@link MinecraftTarget} version, e.g. {@code 1.7.10}. */
    @Input
    public abstract Property<String> getMinecraft();

    @Input
    public abstract Property<ModSpec> getSpec();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws IOException {
        Path out = getOutputDirectory().get().getAsFile().toPath();
        Files.createDirectories(out);
        PluginFiles.clear(out);
        generate(out, MinecraftTarget.of(getMinecraft().get()), getSpec().get(), getResourceDirectories().getFiles(), getLogger());
    }

    static void generate(Path out, MinecraftTarget target, ModSpec spec, Iterable<File> resourceDirs, Logger logger)
            throws IOException {
        for (String path : target.metadataFiles()) {
            File handwritten = find(resourceDirs, path);
            if (handwritten != null) {
                logger.info("Using hand-written {} ({}), not generating one.", path, handwritten);
                continue;
            }
            PluginFiles.write(out.resolve(path), MetadataRenderer.render(spec, target, path).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static File find(Iterable<File> dirs, String path) {
        for (File dir : dirs) {
            File file = new File(dir, path);
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }
}
