package org.yonside.compat.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Links the classes of a version project's unlinked jar against that version's Compat stubs (see
 * {@link Linker}), writing the mod's classes and the library classes they use, relocated, as a class
 * directory.
 */
@CacheableTask
public abstract class LinkCompatClasses extends DefaultTask {

    /** The unlinked jar: the mod's classes, its common jar's and the generated @Mod class. */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInput();

    /** The version's stub jar, which embeds the API. */
    @Classpath
    public abstract ConfigurableFileCollection getStubs();

    /** Internal-name prefix the copied library classes go under, e.g. {@code org/example/mymod/compat/}. */
    @Input
    public abstract Property<String> getRelocationBase();

    @Input
    public abstract Property<String> getMinecraft();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void link() throws IOException {
        Linker linker = Linker.load(getStubs().getFiles(), getRelocationBase().get(), getMinecraft().get());
        Map<String, byte[]> mod = new TreeMap<>();
        Linker.readClasses(List.of(getInput().get().getAsFile()), mod);
        Map<String, byte[]> linked = linker.link(mod);

        Path out = getOutputDirectory().get().getAsFile().toPath();
        Files.createDirectories(out);
        PluginFiles.clear(out);
        for (Map.Entry<String, byte[]> entry : linked.entrySet()) {
            PluginFiles.write(out.resolve(entry.getKey() + ".class"), entry.getValue());
        }
        getLogger().info("Linked {} classes against Compat's {} stubs ({} API types mapped, {} library classes copied).",
            mod.size(), getMinecraft().get(), linker.typeMap().size(), linked.size() - mod.size());
    }
}
