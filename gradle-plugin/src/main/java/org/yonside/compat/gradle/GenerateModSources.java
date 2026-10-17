package org.yonside.compat.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
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
import org.objectweb.asm.Opcodes;

/**
 * Generates the source of a {@code @Mod} class and event listener (see {@link ModSourceGenerator}) for
 * the one class annotated {@code @CompatMod}. Generates nothing when the classes already contain a
 * {@code @Mod} class -- a mod author's own -- or when no class is
 * annotated.
 */
@CacheableTask
public abstract class GenerateModSources extends DefaultTask {

    public static final String COMPAT_MOD = "Lorg/yonside/compat/api/CompatMod;";
    public static final String COMPAT_ADDON = "org/yonside/compat/api/CompatAddon";

    /** The mod's compiled classes, directories or jars: the version project's own and its common jar. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @IgnoreEmptyDirectories
    public abstract ConfigurableFileCollection getClasses();

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
        generate(out, MinecraftTarget.of(getMinecraft().get()), getSpec().get(), getClasses().getFiles(), getLogger());
    }

    static void generate(Path out, MinecraftTarget target, ModSpec spec, Iterable<File> classRoots, Logger logger)
            throws IOException {
        ClassScanner.ScannedClass addon = chooseAddon(ClassScanner.scan(classRoots), target, logger);
        if (addon == null) {
            return;
        }
        String dependencies = target.loader.usesMcmodInfo() ? MetadataRenderer.legacyDependencies(spec, target) : "";
        for (Map.Entry<String, String> source : ModSourceGenerator.generate(target, addon.name, spec.modId(), dependencies).entrySet()) {
            PluginFiles.write(out.resolve(source.getKey()), source.getValue().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** The {@code @CompatMod} class to generate a {@code @Mod} class for, or null to generate none. */
    static ClassScanner.ScannedClass chooseAddon(Map<String, ClassScanner.ScannedClass> classes, MinecraftTarget target,
                                                 Logger logger) {
        List<ClassScanner.ScannedClass> handwritten = ClassScanner.annotatedWith(classes, target.loader.modAnnotation());
        List<ClassScanner.ScannedClass> marked = ClassScanner.annotatedWith(classes, COMPAT_MOD);
        if (!handwritten.isEmpty()) {
            logger.info("Using hand-written @Mod class {}, not generating one.", names(handwritten));
            if (!marked.isEmpty()) {
                logger.warn("{} is annotated @CompatMod, but the hand-written @Mod class {} is what Forge loads,"
                    + " so it has to drive {} itself.", names(marked), names(handwritten), names(marked));
            }
            return null;
        }
        if (marked.isEmpty()) {
            logger.warn("No @Mod class and no class annotated @CompatMod, so this jar has no mod entry point."
                + " Annotate the class that implements CompatAddon with @CompatMod to have one generated.");
            return null;
        }
        if (marked.size() > 1) {
            throw new GradleException("Only one class may be annotated with @CompatMod, found: " + names(marked));
        }
        ClassScanner.ScannedClass addon = marked.get(0);
        String name = addon.javaName();
        if ((addon.access & Opcodes.ACC_PUBLIC) == 0 || (addon.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) != 0) {
            throw new GradleException("@CompatMod class " + name + " must be a public, non-abstract class.");
        }
        if (!addon.publicNoArgConstructor) {
            throw new GradleException("@CompatMod class " + name + " needs a public no-argument constructor:"
                + " the generated @Mod class creates it.");
        }
        if (!ClassScanner.implementsInterface(addon, COMPAT_ADDON, classes)) {
            throw new GradleException("@CompatMod class " + name + " must implement org.yonside.compat.api.CompatAddon.");
        }
        return addon;
    }

    private static String names(List<ClassScanner.ScannedClass> classes) {
        return classes.stream().map(ClassScanner.ScannedClass::javaName).collect(Collectors.joining(", "));
    }
}
