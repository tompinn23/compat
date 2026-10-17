package org.yonside.compat.gradle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Compiles small Java sources into class directories and jars for the scanning tests. */
final class Fixtures {

    /** Stand-ins for Compat Core's API and FML's @Mod annotations: only their names matter. */
    static final Map<String, String> API = Map.of(
        "org/yonside/compat/api/CompatMod.java",
        "package org.yonside.compat.api; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface CompatMod {}",
        "org/yonside/compat/api/CompatWorld.java", "package org.yonside.compat.api; public interface CompatWorld {}",
        "org/yonside/compat/api/CompatAddon.java",
        "package org.yonside.compat.api; public interface CompatAddon { default void onInit() {} default void onWorldLoad(CompatWorld w) {} }",
        "cpw/mods/fml/common/Mod.java",
        "package cpw.mods.fml.common; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Mod { String modid(); }",
        "net/minecraftforge/fml/common/Mod.java",
        "package net.minecraftforge.fml.common; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Mod { String value() default \"\"; }");

    private Fixtures() {}

    /** Compiles {@code sources} (path to content) plus {@link #API} into {@code out}, returning it. */
    static Path compile(Path dir, String name, Map<String, String> sources) throws IOException {
        return compile(dir, name, API, sources);
    }

    /** Compiles exactly {@code sources}. */
    static Path compileOnly(Path dir, String name, Map<String, String> sources) throws IOException {
        return compile(dir, name, Map.of(), sources);
    }

    /** Whether exactly {@code sources} compile. */
    static boolean compiles(Path dir, String name, Map<String, String> sources) throws IOException {
        return javac(dir, name, Map.of(), sources) == 0;
    }

    private static Path compile(Path dir, String name, Map<String, String> base, Map<String, String> sources) throws IOException {
        assertTrue(javac(dir, name, base, sources) == 0, "fixture compiles");
        return dir.resolve(name);
    }

    private static int javac(Path dir, String name, Map<String, String> base, Map<String, String> sources) throws IOException {
        Path src = dir.resolve(name + "-src");
        Path out = dir.resolve(name);
        Files.createDirectories(out);
        List<String> args = new ArrayList<>(List.of("-d", out.toString(), "-proc:none"));
        for (Map<String, String> set : List.of(base, sources)) {
            for (Map.Entry<String, String> source : set.entrySet()) {
                Path file = src.resolve(source.getKey());
                Files.createDirectories(file.getParent());
                Files.writeString(file, source.getValue());
                args.add(file.toString());
            }
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        return compiler.run(null, null, null, args.toArray(String[]::new));
    }

    /** Packs a class directory into a jar, the way a common project's jar reaches a version project. */
    static Path jar(Path classes, Path jar) throws IOException {
        try (OutputStream file = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(file);
             Stream<Path> paths = Files.walk(classes)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                out.putNextEntry(new JarEntry(classes.relativize(path).toString().replace('\\', '/')));
                out.write(Files.readAllBytes(path));
                out.closeEntry();
            }
        }
        return jar;
    }
}
