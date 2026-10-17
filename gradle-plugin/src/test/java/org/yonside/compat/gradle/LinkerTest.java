package org.yonside.compat.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Links a small mod against a fake Minecraft's stubs, then runs it. */
class LinkerTest {

    @TempDir
    Path dir;

    private static final Map<String, String> LIBRARY = Map.of(
        "org/yonside/compat/stub/StubFor.java", """
            package org.yonside.compat.stub;
            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
            public @interface StubFor { Class<?> value(); }
            """,
        "org/yonside/compat/api/CompatWorld.java", """
            package org.yonside.compat.api;
            public interface CompatWorld { boolean isClientSide(); long totalTime(); }
            """,
        "org/yonside/compat/api/CompatAddon.java", """
            package org.yonside.compat.api;
            public interface CompatAddon { default void onWorldLoad(CompatWorld world) {} }
            """,
        "org/yonside/compat/api/Pos.java", """
            package org.yonside.compat.api;
            public final class Pos { public static long pack(int x, int y, int z) { return ((long) x << 40) | ((long) y << 20) | z; }
                                     public static int y(long pos) { return (int) (pos >> 20) & 0xFFFFF; } }
            """,
        "org/yonside/compat/stubs/mc1710/WorldStubs.java", """
            package org.yonside.compat.stubs.mc1710;
            import net.minecraft.world.World;
            import org.yonside.compat.api.CompatWorld;
            @org.yonside.compat.stub.StubFor(CompatWorld.class)
            public record WorldStubs(World world) implements CompatWorld {
                @Override public boolean isClientSide() { return world.isRemote; }
                // Calls its own method and reads the component through the accessor: both unwrapped.
                @Override public long totalTime() { return isClientSide() ? -1 : world().getTotalWorldTime(); }
            }
            """,
        "net/minecraft/world/World.java", """
            package net.minecraft.world;
            public class World { public boolean isRemote; public long time;
                                 public long getTotalWorldTime() { return time; } }
            """);

    private static final String MOD = """
        package com.example;
        import java.util.function.Predicate;
        import org.yonside.compat.api.*;
        public class FixtureMod implements CompatAddon {
            public String last;
            record Seen(CompatWorld world, long pos) {}
            @Override public void onWorldLoad(CompatWorld world) {
                Predicate<CompatWorld> client = CompatWorld::isClientSide;
                Seen seen = new Seen(world, Pos.pack(1, 2, 3));
                last = (world.isClientSide() ? "client" : "server") + " " + seen.world().totalTime()
                    + " y=" + Pos.y(seen.pos()) + " ref=" + client.test(world);
            }
        }
        """;

    private Map<String, byte[]> classes(Path root, String prefix) throws IOException {
        Map<String, byte[]> all = new TreeMap<>();
        Linker.readClasses(List.of(root.toFile()), all);
        all.keySet().removeIf(name -> !name.startsWith(prefix));
        return all;
    }

    @Test
    void linkedModRunsAgainstMinecraftAlone() throws Exception {
        Path compiled = Fixtures.compileOnly(dir, "classes", withMod(MOD));
        Path library = dir.resolve("library");
        for (Map.Entry<String, byte[]> entry : classes(compiled, "org/yonside/compat/").entrySet()) {
            PluginFiles.write(library.resolve(entry.getKey() + ".class"), entry.getValue());
        }

        Linker linker = Linker.load(List.of(library.toFile()), "com/example/compat/", "1.7.10");
        assertEquals(Map.of("org/yonside/compat/api/CompatWorld", "net/minecraft/world/World"), linker.typeMap());
        Map<String, byte[]> linked = linker.link(classes(compiled, "com/example/"));

        // The mod keeps its names; only the library classes it uses come along, relocated. The mapped
        // interface and the marker annotation do not.
        assertEquals(List.of("com/example/FixtureMod", "com/example/FixtureMod$Seen",
            "com/example/compat/api/CompatAddon", "com/example/compat/api/Pos",
            "com/example/compat/stubs/mc1710/WorldStubs"), List.copyOf(linked.keySet()));
        for (Map.Entry<String, byte[]> entry : linked.entrySet()) {
            String text = new String(entry.getValue(), StandardCharsets.ISO_8859_1);
            assertFalse(text.contains("org/yonside/compat"), entry.getKey() + " still refers to Compat's own packages");
        }

        // Run it with only the linked classes and "Minecraft" on the classpath.
        byte[] world = Files.readAllBytes(compiled.resolve("net/minecraft/world/World.class"));
        ClassLoader loader = new ClassLoader(null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                String internal = name.replace('.', '/');
                byte[] bytes = internal.equals("net/minecraft/world/World") ? world : linked.get(internal);
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        Class<?> worldClass = loader.loadClass("net.minecraft.world.World");
        Object minecraftWorld = worldClass.getConstructor().newInstance();
        worldClass.getField("time").setLong(minecraftWorld, 42L);
        Class<?> modClass = loader.loadClass("com.example.FixtureMod");
        Object mod = modClass.getConstructor().newInstance();
        Method onWorldLoad = modClass.getMethod("onWorldLoad", worldClass);
        onWorldLoad.invoke(mod, minecraftWorld);
        assertEquals("server 42 y=2 ref=false", modClass.getField("last").get(mod));
    }

    @Test
    void theCompilerChecksWrappersImplementTheWholeApi() throws Exception {
        Map<String, String> sources = withMod(MOD);
        sources.put("org/yonside/compat/api/CompatWorld.java", """
            package org.yonside.compat.api;
            public interface CompatWorld { boolean isClientSide(); long totalTime(); String name(); }
            """);
        assertFalse(Fixtures.compiles(dir, "incomplete", sources), "a wrapper missing name() must not compile");
    }

    @Test
    void wrappersMayNotExistAtRuntime() throws Exception {
        Map<String, String> sources = withMod(MOD);
        sources.put("org/yonside/compat/stubs/mc1710/WorldStubs.java", """
            package org.yonside.compat.stubs.mc1710;
            import net.minecraft.world.World;
            import org.yonside.compat.api.CompatWorld;
            @org.yonside.compat.stub.StubFor(CompatWorld.class)
            public record WorldStubs(World world) implements CompatWorld {
                @Override public boolean isClientSide() { return world.isRemote; }
                @Override public long totalTime() { return new WorldStubs(world).isClientSide() ? 0 : 1; }
            }
            """);
        Path compiled = Fixtures.compileOnly(dir, "constructs", sources);
        GradleException e = assertThrows(GradleException.class,
            () -> Linker.load(List.of(compiled.toFile()), "com/example/compat/", "1.7.10"));
        assertTrue(e.getMessage().contains("WorldStubs.totalTime"), e.getMessage());
    }

    @Test
    void anApiMethodWithoutAStubFailsTheLink() throws Exception {
        // A stubs jar built before the API gained a method: the wrapper compiled then, the mod calls it now.
        Map<String, String> sources = withMod("""
            package com.example;
            public class FixtureMod { public static String name(org.yonside.compat.api.CompatWorld w) { return w.name(); } }
            """);
        Path stubs = Fixtures.compileOnly(dir, "stubs", sources.entrySet().stream()
            .filter(e -> !e.getKey().startsWith("com/")).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        sources.put("org/yonside/compat/api/CompatWorld.java", """
            package org.yonside.compat.api;
            public interface CompatWorld { boolean isClientSide(); long totalTime(); String name(); }
            """);
        sources.keySet().removeIf(path -> path.contains("/stubs/"));
        Path mod = Fixtures.compileOnly(dir, "mod", sources);

        Linker linker = Linker.load(List.of(stubs.toFile()), "com/example/compat/", "1.7.10");
        GradleException e = assertThrows(GradleException.class, () -> linker.link(classes(mod, "com/example/")));
        assertTrue(e.getMessage().contains("org.yonside.compat.api.CompatWorld.name()Ljava/lang/String;, called from"
            + " com.example.FixtureMod.name, has no stub for Minecraft 1.7.10"), e.getMessage());
        assertTrue(e.getMessage().contains("Implement it in the @StubFor(CompatWorld.class) wrapper"), e.getMessage());
    }

    private static Map<String, String> withMod(String mod) {
        Map<String, String> sources = new TreeMap<>(LIBRARY);
        sources.put("com/example/FixtureMod.java", mod);
        return sources;
    }
}
