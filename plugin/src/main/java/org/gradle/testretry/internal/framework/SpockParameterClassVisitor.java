/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.testretry.internal.framework;

import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;
import static org.objectweb.asm.Opcodes.ASM7;

/**
 * Class visitor that identifies unparameterized test method names.
 */
final class SpockParameterClassVisitor extends ClassVisitor {

    // A valid Java identifier https://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html#jls-3.8 including methods
    private static final String SPOCK_PARAM_PATTERN = "#[\\p{L}\\d$_.()&&[^#\\s]]+";
    private static final String WILDCARD = ".*";

    private final String failedTestMethodNameMaybeParameterized;
    private final JvmTestExecutionSpec spec;
    private final SpockParameterMethodVisitor spockMethodVisitor = new SpockParameterMethodVisitor();

    private boolean foundLiteralMethod;
    private final Set<String> matchedMethodNames = new HashSet<>();

    public SpockParameterClassVisitor(String testMethodName, JvmTestExecutionSpec spec) {
        super(ASM7);
        this.failedTestMethodNameMaybeParameterized = testMethodName;
        this.spec = spec;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return spockMethodVisitor;
    }

    @Override
    public void visitEnd() {
        Set<String> matchingNames = spockMethodVisitor.annotationVisitor.testMethodPatterns.stream()
            .filter(methodPattern -> {
                // Replace params in the method name with .*
                String methodPatternRegex = Arrays.stream(methodPattern.split(SPOCK_PARAM_PATTERN))
                    .map(Pattern::quote)
                    .collect(Collectors.joining(WILDCARD));

                // For when no params in name - [iterationNum] implicitly added to end
                methodPatternRegex += WILDCARD;
                return methodPattern.equals(failedTestMethodNameMaybeParameterized) || failedTestMethodNameMaybeParameterized.matches(methodPatternRegex);
            })
            .collect(Collectors.toSet());

        if (matchingNames.contains(failedTestMethodNameMaybeParameterized)) {
            foundLiteralMethod = true;
            matchedMethodNames.add(failedTestMethodNameMaybeParameterized);
        } else {
            matchedMethodNames.addAll(matchingNames);
        }
    }

    public boolean isFoundLiteralMethodName() {
        return foundLiteralMethod;
    }

    public Set<String> getMatchedMethodNames() {
        return matchedMethodNames;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        if (superName != null && !superName.equals("java/lang/Object")) {
            Stream<File> classpathDirectories = Stream.concat(stream(spec.getTestClassesDirs().spliterator(), false),
                stream(spec.getClasspath().spliterator(), false).filter(File::isDirectory)
            );

            Optional<Path> parentPath = classpathDirectories
                .map(dir -> Paths.get(dir.getAbsolutePath(), superName + ".class"))
                .filter(classFile -> Files.exists(classFile))
                .findAny();

            if (parentPath.isPresent()) {
                try (InputStream fis = new FileInputStream(parentPath.get().toFile())) {
                    readParentClass(fis);
                } catch (IOException ignored) {
                    // we tried, move on to looking in the jar classpath
                }
            } else {
                for (File file : spec.getClasspath()) {
                    if (!file.getName().endsWith(".jar")) {
                        continue;
                    }

                    try (JarFile jarFile = new JarFile(file)) {
                        Optional<JarEntry> classFile = jarFile.stream()
                            .filter(maybeClass -> maybeClass.getName().equals(superName + ".class"))
                            .findAny();

                        if (classFile.isPresent()) {
                            try (InputStream is = jarFile.getInputStream(classFile.get())) {
                                readParentClass(is);
                                return;
                            }
                        }
                    } catch (IOException ignored) {
                        // we tried... this file looks corrupt, move on to the next jar
                    }
                }
            }
        }
    }

    private void readParentClass(InputStream is) {
        try {
            ClassReader classReader = new ClassReader(is);
            classReader.accept(this, 0);
        } catch (IOException ignored) {
            // we did our best...
        }
    }

    private static final class SpockParameterMethodVisitor extends MethodVisitor {

        private final SpockFeatureMetadataAnnotationVisitor annotationVisitor = new SpockFeatureMetadataAnnotationVisitor();

        public SpockParameterMethodVisitor() {
            super(ASM7);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.contains("org/spockframework/runtime/model/FeatureMetadata")) {
                return annotationVisitor;
            }
            return null;
        }

        /**
         * Looking for signatures like:
         * org/spockframework/runtime/model/FeatureMetadata;(
         * line=15,
         * name="unrolled with param #param",
         * ordinal=0,
         * blocks={...},
         * parameterNames={"param", "result"}
         * )
         */
        private static final class SpockFeatureMetadataAnnotationVisitor extends AnnotationVisitor {

            private final List<String> testMethodPatterns = new ArrayList<>();

            public SpockFeatureMetadataAnnotationVisitor() {
                super(ASM7);
            }

            @Override
            public void visit(String name, Object value) {
                if ("name".equals(name)) {
                    testMethodPatterns.add((String) value);
                }
            }

        }

    }

}

