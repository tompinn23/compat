package org.yonside.compat.gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.gradle.api.GradleException;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Links a mod's classes against one Minecraft version's Compat stubs, the way JvmDowngrader links code
 * against its Java API stubs:
 * <ul>
 *   <li>each API interface with a {@code @StubFor} class becomes that class's target type, e.g.
 *       {@code CompatWorld} becomes {@code net/minecraft/world/World}, in every descriptor, signature,
 *       cast and {@code instanceof};</li>
 *   <li>each call to such an interface's method becomes a static call to the stub method of the same
 *       name taking the target type first;</li>
 *   <li>every other library class the mod reaches (stubs, API value types like {@code Pos}, the
 *       {@code CompatAddon} interface) is copied into the output, relocated under the mod's package so no
 *       two mods' copies ever meet.</li>
 * </ul>
 * The mod's own classes keep their names. Nothing of Compat remains to install at runtime.
 */
final class Linker {

    static final String STUB_FOR = "Lorg/yonside/compat/stub/StubFor;";

    private record Stub(String owner, String name, String descriptor) {}

    /** API interface to the Minecraft type replacing it. */
    private final Map<String, String> typeMap = new HashMap<>();
    /** {@code api.name(args-after-mapping)ret} to the stub method implementing it. */
    private final Map<String, Stub> stubs = new HashMap<>();
    /** Every library class, by internal name. */
    private final Map<String, byte[]> library = new HashMap<>();
    private final String relocationBase;
    private final String minecraft;

    private Linker(String relocationBase, String minecraft) {
        this.relocationBase = relocationBase.endsWith("/") ? relocationBase : relocationBase + "/";
        this.minecraft = minecraft;
    }

    /**
     * @param libraries      the stub jars (each with the API embedded), or class directories
     * @param relocationBase where copied library classes go, e.g. {@code org/example/mymod/compat/}
     * @param minecraft      the version, for error messages
     */
    static Linker load(Iterable<File> libraries, String relocationBase, String minecraft) throws IOException {
        Linker linker = new Linker(relocationBase, minecraft);
        readClasses(libraries, linker.library);
        for (byte[] bytes : linker.library.values()) {
            linker.indexStubs(bytes);
        }
        return linker;
    }

    private void indexStubs(byte[] bytes) {
        String[] apiAndTarget = new String[2];
        ClassReader reader = new ClassReader(bytes);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!descriptor.equals(STUB_FOR)) {
                    return null;
                }
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        if (name.equals("api")) {
                            apiAndTarget[0] = ((Type) value).getInternalName();
                        } else if (name.equals("target")) {
                            apiAndTarget[1] = ((Type) value).getInternalName();
                        }
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (apiAndTarget[0] == null) {
            return;
        }
        // Not "api": inside the ClassVisitor below that would be ASM's own api field.
        String apiType = apiAndTarget[0];
        String target = apiAndTarget[1];
        String previous = typeMap.put(apiType, target);
        if (previous != null && !previous.equals(target)) {
            throw new GradleException("Two @StubFor classes map " + apiType + ", to " + previous + " and " + target);
        }
        String owner = reader.getClassName();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                Type[] args = Type.getArgumentTypes(descriptor);
                boolean stub = (access & Opcodes.ACC_STATIC) != 0 && (access & Opcodes.ACC_PUBLIC) != 0
                    && args.length > 0 && args[0].getSort() == Type.OBJECT && args[0].getInternalName().equals(target);
                if (stub) {
                    Type[] rest = new Type[args.length - 1];
                    System.arraycopy(args, 1, rest, 0, rest.length);
                    String key = apiType + "." + name + Type.getMethodDescriptor(Type.getReturnType(descriptor), rest);
                    stubs.put(key, new Stub(owner, name, descriptor));
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    /** The API types this linker replaces, to their Minecraft types. */
    Map<String, String> typeMap() {
        return Map.copyOf(typeMap);
    }

    /**
     * Links {@code mod} (internal name to class bytes) and returns every output class by internal name:
     * the mod's classes under their own names, then the library classes they need, relocated.
     */
    Map<String, byte[]> link(Map<String, byte[]> mod) {
        Map<String, byte[]> out = new TreeMap<>();
        Deque<String> needed = new ArrayDeque<>();
        LinkRemapper remapper = new LinkRemapper(needed);
        for (Map.Entry<String, byte[]> entry : mod.entrySet()) {
            out.put(entry.getKey(), transform(entry.getValue(), remapper));
        }
        while (!needed.isEmpty()) {
            String name = needed.pop();
            String relocated = relocate(name);
            if (!out.containsKey(relocated)) {
                out.put(relocated, transform(library.get(name), remapper));
            }
        }
        return out;
    }

    private byte[] transform(byte[] bytes, Remapper remapper) {
        ClassReader reader = new ClassReader(bytes);
        // No frames are recomputed: a redirected call has the same stack effect, and every type changes
        // consistently, so the existing frames stay valid with their names remapped.
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new StubCalls(new ClassRemapper(writer, remapper), reader.getClassName()), 0);
        return writer.toByteArray();
    }

    private String relocate(String name) {
        String prefix = "org/yonside/compat/";
        return relocationBase + (name.startsWith(prefix) ? name.substring(prefix.length()) : name);
    }

    /** Maps API types to Minecraft types and library classes to their relocated copies, noting each one used. */
    private final class LinkRemapper extends Remapper {

        private final Deque<String> needed;
        private final Map<String, String> seen = new LinkedHashMap<>();

        LinkRemapper(Deque<String> needed) {
            super(Opcodes.ASM9);
            this.needed = needed;
        }

        @Override
        public String map(String internalName) {
            String target = typeMap.get(internalName);
            if (target != null) {
                return target;
            }
            if (library.containsKey(internalName)) {
                return seen.computeIfAbsent(internalName, name -> {
                    needed.push(name);
                    return relocate(name);
                });
            }
            return internalName;
        }
    }

    /** Turns calls (and method references) to mapped API interfaces into static stub calls; drops @StubFor. */
    private final class StubCalls extends ClassVisitor {

        private final String className;

        StubCalls(ClassVisitor next, String className) {
            super(Opcodes.ASM9, next);
            this.className = className;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return descriptor.equals(STUB_FOR) ? null : super.visitAnnotation(descriptor, visible);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            String method = name;
            return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String callee, String desc, boolean isInterface) {
                    if (typeMap.containsKey(owner) && opcode != Opcodes.INVOKESTATIC) {
                        Stub stub = stubFor(owner, callee, desc, method);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, stub.owner(), stub.name(), receiverFirst(owner, desc), false);
                    } else {
                        super.visitMethodInsn(opcode, owner, callee, desc, isInterface);
                    }
                }

                @Override
                public void visitInvokeDynamicInsn(String indyName, String indyDesc, Handle bootstrap, Object... args) {
                    Object[] linked = args.clone();
                    for (int i = 0; i < linked.length; i++) {
                        if (linked[i] instanceof Handle handle && typeMap.containsKey(handle.getOwner())
                                && handle.getTag() != Opcodes.H_INVOKESTATIC && handle.getTag() >= Opcodes.H_INVOKEVIRTUAL) {
                            Stub stub = stubFor(handle.getOwner(), handle.getName(), handle.getDesc(), method);
                            linked[i] = new Handle(Opcodes.H_INVOKESTATIC, stub.owner(), stub.name(),
                                receiverFirst(handle.getOwner(), handle.getDesc()), false);
                        }
                    }
                    super.visitInvokeDynamicInsn(indyName, indyDesc, bootstrap, linked);
                }
            };
        }

        private Stub stubFor(String api, String name, String descriptor, String caller) {
            String key = api + "." + name + mapDescriptor(descriptor);
            Stub stub = stubs.get(key);
            if (stub == null) {
                throw new GradleException(api.replace('/', '.') + "." + name + descriptor + ", called from "
                    + className.replace('/', '.') + "." + caller + ", has no stub for Minecraft " + minecraft
                    + ". Its @StubFor(api = " + api.substring(api.lastIndexOf('/') + 1) + ".class) class needs a"
                    + " public static " + name + " taking " + typeMap.get(api).replace('/', '.') + " first.");
            }
            return stub;
        }
    }

    private static String receiverFirst(String owner, String descriptor) {
        return "(L" + owner + ";" + descriptor.substring(1);
    }

    private String mapDescriptor(String descriptor) {
        Type[] args = Type.getArgumentTypes(descriptor);
        for (int i = 0; i < args.length; i++) {
            args[i] = mapType(args[i]);
        }
        return Type.getMethodDescriptor(mapType(Type.getReturnType(descriptor)), args);
    }

    private Type mapType(Type type) {
        if (type.getSort() == Type.OBJECT && typeMap.containsKey(type.getInternalName())) {
            return Type.getObjectType(typeMap.get(type.getInternalName()));
        }
        return type;
    }

    /** Every class in directories or jars, by internal name; earlier roots win. */
    static void readClasses(Iterable<File> roots, Map<String, byte[]> into) throws IOException {
        for (File root : roots) {
            if (root.isDirectory()) {
                try (Stream<Path> paths = Files.walk(root.toPath())) {
                    for (Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                        put(into, Files.readAllBytes(path));
                    }
                }
            } else if (root.isFile()) {
                try (ZipFile jar = new ZipFile(root)) {
                    Enumeration<? extends ZipEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (entry.getName().endsWith(".class") && !entry.getName().startsWith("META-INF/")
                                && !entry.getName().endsWith("module-info.class")) {
                            try (InputStream in = jar.getInputStream(entry)) {
                                put(into, in.readAllBytes());
                            }
                        }
                    }
                }
            }
        }
    }

    private static void put(Map<String, byte[]> into, byte[] bytes) {
        into.putIfAbsent(new ClassReader(bytes).getClassName(), bytes);
    }
}
