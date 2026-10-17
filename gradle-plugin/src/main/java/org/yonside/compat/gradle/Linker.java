package org.yonside.compat.gradle;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
import org.objectweb.asm.tree.MethodNode;

/**
 * Links a mod's classes against one Minecraft version's Compat stubs, the way JvmDowngrader links code
 * against its Java API stubs:
 * <ul>
 *   <li>each API interface with a {@code @StubFor} wrapper (see {@link StubWrapper}) becomes the type it
 *       wraps, e.g.
 *       {@code CompatWorld} becomes {@code net/minecraft/world/World}, in every descriptor, signature,
 *       cast and {@code instanceof};</li>
 *   <li>each call to such an interface's method becomes a static call to the unwrapped wrapper's method
 *       of the same name, which takes the wrapped type first;</li>
 *   <li>each API class with a {@code @StubFor} class stub becomes that stub, so a mod's subclass of
 *       {@code CompatBlock} extends the version's {@code LinkedBlock}, itself a Minecraft {@code Block};</li>
 *   <li>every other library class the mod reaches (stubs, API value types like {@code Pos}, the
 *       {@code CompatAddon} interface) is copied into the output, relocated under the mod's package so no
 *       two mods' copies ever meet.</li>
 * </ul>
 * The mod's own classes keep their names. Nothing of Compat remains to install at runtime.
 */
final class Linker {

    static final String STUB_FOR = "Lorg/yonside/compat/stub/StubFor;";

    private record Stub(String owner, String name, String descriptor) {}

    /** API type to what replaces it: a Minecraft type (wrappers) or a stub class (class stubs). */
    private final Map<String, String> typeMap = new HashMap<>();
    /** The API interfaces whose calls become static stub calls. */
    private final Set<String> wrapped = new HashSet<>();
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
        ClassScanner.readClasses(libraries, linker.library);
        // Unwrap every @StubFor wrapper first, so the whole type map is known before stub methods are
        // keyed by their (mapped) descriptors.
        List<StubWrapper.Unwrapped> wrappers = new ArrayList<>();
        for (byte[] bytes : linker.library.values()) {
            StubWrapper.Unwrapped unwrapped = StubWrapper.unwrap(bytes);
            if (unwrapped != null) {
                wrappers.add(unwrapped);
                String previous = linker.typeMap.put(unwrapped.api(), unwrapped.target());
                if (previous != null) {
                    throw new GradleException("Two @StubFor wrappers implement " + unwrapped.api().replace('/', '.'));
                }
            }
        }
        for (StubWrapper.Unwrapped unwrapped : wrappers) {
            if (!unwrapped.classStub()) {
                linker.wrapped.add(unwrapped.api());
                linker.library.put(unwrapped.node().name, StubWrapper.bytes(unwrapped.node()));
                linker.index(unwrapped);
            }
        }
        return linker;
    }

    private void index(StubWrapper.Unwrapped unwrapped) {
        for (MethodNode method : unwrapped.node().methods) {
            Type[] args = Type.getArgumentTypes(method.desc);
            if ((method.access & Opcodes.ACC_STATIC) == 0 || (method.access & Opcodes.ACC_PUBLIC) == 0 || args.length == 0
                    || args[0].getSort() != Type.OBJECT || !args[0].getInternalName().equals(unwrapped.target())) {
                continue;
            }
            Type[] rest = Arrays.copyOfRange(args, 1, args.length);
            String key = unwrapped.api() + "." + method.name
                + mapDescriptor(Type.getMethodDescriptor(Type.getReturnType(method.desc), rest));
            stubs.put(key, new Stub(unwrapped.node().name, method.name, method.desc));
        }
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
        LinkRemapper remapper = new LinkRemapper(needed, mod.keySet());
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
        private final Set<String> modClasses;
        private final Map<String, String> seen = new LinkedHashMap<>();

        LinkRemapper(Deque<String> needed, Set<String> modClasses) {
            super(Opcodes.ASM9);
            this.needed = needed;
            this.modClasses = modClasses;
        }

        @Override
        public String map(String internalName) {
            // An API type becomes what replaces it; a class stub is then relocated like any library class.
            internalName = typeMap.getOrDefault(internalName, internalName);
            // The mod's own classes keep their names, even if a library happens to contain them too.
            if (library.containsKey(internalName) && !modClasses.contains(internalName)) {
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
                    if (wrapped.contains(owner) && opcode != Opcodes.INVOKESTATIC) {
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
                        if (linked[i] instanceof Handle handle && wrapped.contains(handle.getOwner())
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
                    + ". Implement it in the @StubFor(" + api.substring(api.lastIndexOf('/') + 1) + ".class) wrapper"
                    + " for that version.");
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
}
