package org.yonside.compat.gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Reads class files out of directories and jars, and their headers and class-level annotations. */
final class ClassScanner {

    private ClassScanner() {}

    static final class ScannedClass extends ClassVisitor {

        String name;
        String superName;
        String[] interfaces;
        int access;
        final Set<String> annotations = new HashSet<>();
        boolean publicNoArgConstructor;

        ScannedClass() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.access = access;
            this.name = name;
            this.superName = superName;
            this.interfaces = interfaces == null ? new String[0] : interfaces;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            annotations.add(descriptor);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (name.equals("<init>") && descriptor.equals("()V") && (access & Opcodes.ACC_PUBLIC) != 0) {
                publicNoArgConstructor = true;
            }
            return null;
        }

        String javaName() {
            return name.replace('/', '.');
        }
    }

    /** Every class in {@code roots}, which may be directories or jars; missing roots are skipped. */
    static Map<String, ScannedClass> scan(Iterable<File> roots) throws IOException {
        Map<String, byte[]> bytes = new HashMap<>();
        readClasses(roots, bytes);
        Map<String, ScannedClass> classes = new HashMap<>();
        for (byte[] classFile : bytes.values()) {
            ScannedClass scanned = new ScannedClass();
            new ClassReader(classFile).accept(scanned, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            classes.put(scanned.name, scanned);
        }
        return classes;
    }

    /** Every class file in directories or jars, by internal name; earlier roots win, missing ones are skipped. */
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

    static List<ScannedClass> annotatedWith(Map<String, ScannedClass> classes, String descriptor) {
        List<ScannedClass> found = new ArrayList<>();
        for (ScannedClass scanned : classes.values()) {
            if (scanned.annotations.contains(descriptor)) {
                found.add(scanned);
            }
        }
        found.sort((a, b) -> a.name.compareTo(b.name));
        return found;
    }

    /**
     * Whether {@code start} implements {@code iface}, walking supertypes through the scanned classes. If
     * the walk leaves them (a library base class) the answer is unknowable here, so give the benefit of
     * the doubt -- the JVM still reports a mismatch when the class loads.
     */
    static boolean implementsInterface(ScannedClass start, String iface, Map<String, ScannedClass> classes) {
        List<String> pending = new ArrayList<>();
        pending.add(start.name);
        Set<String> seen = new HashSet<>();
        while (!pending.isEmpty()) {
            String name = pending.remove(pending.size() - 1);
            if (name.equals(iface)) {
                return true;
            }
            if (!seen.add(name)) {
                continue;
            }
            ScannedClass scanned = classes.get(name);
            if (scanned == null) {
                // JDK types never implement our interfaces; anything else is a library we cannot see into.
                if (!name.startsWith("java/")) {
                    return true;
                }
                continue;
            }
            if (scanned.superName != null) {
                pending.add(scanned.superName);
            }
            pending.addAll(List.of(scanned.interfaces));
        }
        return false;
    }
}
