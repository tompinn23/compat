package org.yonside.compat.gradle;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.gradle.api.GradleException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Turns a {@code @StubFor} wrapper -- {@code record WorldStubs(World world) implements CompatWorld} --
 * into a class of static methods the linker can call: {@code static boolean isClientSide(World world)}.
 *
 * <p>The receiver moves into the first parameter, which takes the same local slot 0, so method bodies
 * hardly change: reading the component ({@code this.world} or {@code world()}) becomes the parameter
 * itself, and calling another of the wrapper's methods becomes a static call. The record's constructor,
 * field, accessor, {@code equals}, {@code hashCode} and {@code toString} go.
 */
final class StubWrapper {

    private static final Set<String> OBJECT_METHODS = Set.of(
        "equals(Ljava/lang/Object;)Z", "hashCode()I", "toString()Ljava/lang/String;");

    /** The API interface implemented, the wrapped type, and the unwrapped class. */
    record Unwrapped(String api, String target, ClassNode node) {}

    private StubWrapper() {}

    /** Null if {@code bytes} is not a {@code @StubFor} class. */
    static Unwrapped unwrap(byte[] bytes) {
        ClassNode in = new ClassNode();
        new ClassReader(bytes).accept(in, ClassReader.EXPAND_FRAMES);
        String api = apiOf(in);
        if (api == null) {
            return null;
        }
        String name = in.name;
        String where = "@StubFor wrapper " + name.replace('/', '.');
        if (!in.interfaces.contains(api)) {
            throw new GradleException(where + " must implement " + api.replace('/', '.') + ".");
        }
        List<FieldNode> fields = in.fields.stream().filter(f -> (f.access & Opcodes.ACC_STATIC) == 0).toList();
        if (fields.size() != 1 || Type.getType(fields.get(0).desc).getSort() != Type.OBJECT) {
            throw new GradleException(where + " must have exactly one component, the Minecraft object it wraps.");
        }
        FieldNode component = fields.get(0);
        String target = Type.getType(component.desc).getInternalName();
        String accessor = component.name + "()" + component.desc;

        ClassNode out = new ClassNode();
        out.version = in.version;
        out.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER;
        out.name = name;
        out.superName = "java/lang/Object";
        out.sourceFile = in.sourceFile;
        out.invisibleAnnotations = in.invisibleAnnotations;
        out.innerClasses = in.innerClasses;
        out.nestHostClass = in.nestHostClass;
        out.nestMembers = in.nestMembers;
        out.fields = new ArrayList<>(in.fields.stream().filter(f -> (f.access & Opcodes.ACC_STATIC) != 0).toList());

        for (MethodNode method : in.methods) {
            String signature = method.name + method.desc;
            if ((method.access & Opcodes.ACC_STATIC) != 0) {
                out.methods.add(method);
                continue;
            }
            if (method.name.equals("<init>") || (method.access & Opcodes.ACC_BRIDGE) != 0
                    || signature.equals(accessor) || OBJECT_METHODS.contains(signature)) {
                continue;
            }
            String where2 = where + "." + method.name;
            method.access = (method.access | Opcodes.ACC_STATIC) & ~(Opcodes.ACC_FINAL | Opcodes.ACC_SYNCHRONIZED);
            method.desc = "(" + component.desc + method.desc.substring(1);
            method.signature = null;
            rewriteBody(method.instructions, name, component, accessor, target, where2);
            if (method.localVariables != null) {
                for (LocalVariableNode local : method.localVariables) {
                    if (local.index == 0 && local.desc.equals("L" + name + ";")) {
                        local.desc = component.desc;
                        local.name = component.name;
                        local.signature = null;
                    }
                }
            }
            out.methods.add(method);
        }
        return new Unwrapped(api, target, out);
    }

    static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void rewriteBody(InsnList instructions, String name, FieldNode component, String accessor,
                                    String target, String where) {
        ListIterator<AbstractInsnNode> it = instructions.iterator();
        while (it.hasNext()) {
            AbstractInsnNode insn = it.next();
            if (insn instanceof FieldInsnNode field && field.owner.equals(name)) {
                if (field.getOpcode() == Opcodes.GETFIELD && field.name.equals(component.name)) {
                    // this.world: the receiver on the stack already is the world.
                    it.remove();
                } else {
                    throw new GradleException(where + " uses the wrapper's field " + field.name
                        + " other than to read the wrapped object.");
                }
            } else if (insn instanceof MethodInsnNode call && call.owner.equals(name)) {
                if (call.name.equals("<init>")) {
                    throw new GradleException(where + " constructs the wrapper; stubs never exist at runtime.");
                }
                if (call.getOpcode() == Opcodes.INVOKESTATIC) {
                    continue;
                }
                if ((call.name + call.desc).equals(accessor)) {
                    it.remove();
                } else {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.desc = "(" + component.desc + call.desc.substring(1);
                    call.itf = false;
                }
            } else if (insn instanceof TypeInsnNode type && type.desc.equals(name)) {
                throw new GradleException(where + " uses the wrapper type itself (opcode " + type.getOpcode()
                    + "); stubs never exist at runtime.");
            } else if (insn instanceof FrameNode frame) {
                replace(frame.local, name, target);
                replace(frame.stack, name, target);
            }
        }
    }

    private static void replace(List<Object> types, String from, String to) {
        if (types != null) {
            types.replaceAll(type -> from.equals(type) ? to : type);
        }
    }

    private static String apiOf(ClassNode node) {
        if (node.invisibleAnnotations == null) {
            return null;
        }
        for (AnnotationNode annotation : node.invisibleAnnotations) {
            if (annotation.desc.equals(Linker.STUB_FOR) && annotation.values != null) {
                for (int i = 0; i < annotation.values.size(); i += 2) {
                    if (annotation.values.get(i).equals("value")) {
                        return ((Type) annotation.values.get(i + 1)).getInternalName();
                    }
                }
            }
        }
        return null;
    }
}
