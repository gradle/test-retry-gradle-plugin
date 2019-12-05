package org.gradle.plugins.testretry.visitors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.*;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.ASM7;

public class TestNGClassVisitor extends ClassVisitor {
    private String currentMethod;
    private Map<String, List<String>> dependsOn = new HashMap<>();
    private Map<String, List<String>> dependedOn = new HashMap<>();

    public TestNGClassVisitor() {
        super(ASM7);
    }

    public Set<String> dependsOn(String method) {
        Set<String> dependentChain = new HashSet<>();
        dependentChain.add(method);

        List<String> search = Collections.singletonList(method);
        while (!search.isEmpty()) {
            search = search.stream()
                .flatMap(upstream -> dependsOn.getOrDefault(upstream, Collections.emptyList()).stream())
                .filter(upstream -> !dependentChain.contains(upstream))
                .collect(Collectors.toList());
            dependentChain.addAll(search);
        }

        search = Collections.singletonList(method);
        while (!search.isEmpty()) {
            search = search.stream()
                    .flatMap(downstream -> dependedOn.getOrDefault(downstream, Collections.emptyList()).stream())
                    .filter(downstream -> !dependentChain.contains(downstream))
                    .collect(Collectors.toList());
            dependentChain.addAll(search);
        }

        return dependentChain;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        this.currentMethod = name;
        return new TestNGMethodVisitor();
    }

    class TestNGMethodVisitor extends MethodVisitor {
        public TestNGMethodVisitor() {
            super(ASM7);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if(descriptor.contains("org/testng/annotations/Test")) {
                return new TestNGTestAnnotationVisitor();
            }
            return null;
        }
    }

    class TestNGTestAnnotationVisitor extends AnnotationVisitor {
        public TestNGTestAnnotationVisitor() {
            super(ASM7);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if("dependsOnMethods".equals(name)) {
                return new TestNGTestDependsOnAnnotationVisitor();
            }
            return null;
        }
    }

    class TestNGTestDependsOnAnnotationVisitor extends AnnotationVisitor {
        public TestNGTestDependsOnAnnotationVisitor() {
            super(ASM7);
        }

        @Override
        public void visit(String name, Object value) {
            dependsOn.compute(currentMethod, (m, acc) -> {
                if(acc == null) {
                    acc = new ArrayList<>();
                }
                acc.add((String) value);
                return acc;
            });

            dependedOn.compute((String) value, (m, acc) -> {
                if(acc == null) {
                    acc = new ArrayList<>();
                }
                acc.add(currentMethod);
                return acc;
            });
        }
    }
}