package com.otectus.immersivejewelry.integration.diegesis;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the compatibility probe complete: reads Jewelry's compiled classes and finds every Diegesis method it calls, every
 * Diegesis field it reads, every Diegesis interface it implements and every library method it overrides. Each must be in
 * {@link DiegesisCompat#MEMBERS} (interfaces as their exact set of abstract methods), so a new use of the library can never
 * skip the probe: add it there, under the feature that needs it, and guard the call.
 */
class DiegesisUsageTest {
    private static final String LIB = "com/pockyl/diegesis/";
    private static final Path CLASSES = Path.of("build/classes/java/main");

    /** "owner.name(params)" in internal names, the way bytecode names a member. */
    private static String key(String owner, String name, String descriptor) {
        StringBuilder out = new StringBuilder(owner).append('.').append(name).append('(');
        for (Type t : Type.getArgumentTypes(descriptor)) out.append(t.getDescriptor());
        return out.append(')').toString();
    }

    private static Set<String> listed() {
        Set<String> out = new HashSet<>();
        for (DiegesisCompat.Member m : DiegesisCompat.MEMBERS) {
            String owner = m.type().replace('.', '/');
            switch (m.kind()) {
                case METHOD, CONSTRUCTOR -> {
                    StringBuilder d = new StringBuilder("(");
                    for (String p : m.params()) d.append(descriptor(p));
                    out.add(key(owner, m.name(), d.append(")V").toString()));
                }
                case FIELD -> out.add(owner + "#" + m.name());
                case ABSTRACTS -> out.add(owner + "!");
                default -> {
                }
            }
        }
        return out;
    }

    private static String descriptor(String type) {
        return switch (type) {
            case "int" -> "I";
            case "float" -> "F";
            case "double" -> "D";
            case "boolean" -> "Z";
            case "char" -> "C";
            case "long" -> "J";
            default -> type.endsWith("[]") ? "[" + descriptor(type.substring(0, type.length() - 2)) : "L" + type.replace('.', '/') + ";";
        };
    }

    @Test
    void everyUseOfTheLibraryIsProbed() throws IOException {
        assertTrue(Files.isDirectory(CLASSES), "compiled classes at " + CLASSES.toAbsolutePath());
        Set<String> uses = new TreeSet<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(CLASSES)) {
            files = walk.filter(p -> p.toString().endsWith(".class")).toList();
        }
        for (Path file : files) {
            try (InputStream in = Files.newInputStream(file)) {
                new ClassReader(in).accept(new Collector(uses), ClassReader.SKIP_FRAMES);
            }
        }
        Set<String> missing = new TreeSet<>(uses);
        missing.removeAll(listed());
        assertEquals(Set.of(), missing, "Diegesis members Jewelry uses that DiegesisCompat.MEMBERS does not probe");
        assertTrue(uses.size() > 40, "found " + uses.size() + " uses: the scan works");
    }

    /** Collects library members used by one class. */
    private static final class Collector extends ClassVisitor {
        private final Set<String> uses;
        private final List<Class<?>> libraryInterfaces = new ArrayList<>();

        Collector(Set<String> uses) {
            super(Opcodes.ASM9);
            this.uses = uses;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            for (String i : interfaces == null ? new String[0] : interfaces) {
                if (!i.startsWith(LIB)) continue;
                uses.add(i + "!");
                try {
                    libraryInterfaces.add(Class.forName(i.replace('/', '.'), false, DiegesisUsageTest.class.getClassLoader()));
                } catch (ClassNotFoundException e) {
                    throw new AssertionError(e);
                }
            }
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            // A library default this class overrides: the library must still call it by that name and signature.
            for (Class<?> i : libraryInterfaces) {
                for (Method m : i.getMethods()) {
                    if (m.isDefault() && m.getName().equals(name) && Type.getMethodDescriptor(m).equals(descriptor)) {
                        uses.add(key(i.getName().replace('.', '/'), name, descriptor));
                    }
                }
            }
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    if (owner.startsWith(LIB)) uses.add(key(owner, name, descriptor));
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                    if (owner.startsWith(LIB)) uses.add(owner + "#" + name);
                }
            };
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return null;
        }
    }
}
