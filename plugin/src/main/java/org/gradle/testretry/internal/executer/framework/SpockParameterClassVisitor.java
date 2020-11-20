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
package org.gradle.testretry.internal.executer.framework;

import org.gradle.testretry.internal.executer.TestsReader;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.ASM7;

/**
 * Class visitor that identifies unparameterized test method names.
 */
final class SpockParameterClassVisitor extends TestsReader.Visitor<Map<String, List<String>>> {

    // A valid Java identifier https://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html#jls-3.8 including methods
    private static final String SPOCK_PARAM_PATTERN = "#[\\p{L}\\d$_.()&&[^#\\s]]+";
    private static final String WILDCARD = ".*";

    private final Set<String> failedTestNames;
    private final TestsReader testsReader;
    private final SpockParameterMethodVisitor spockMethodVisitor = new SpockParameterMethodVisitor();
    private boolean isSpec;

    public SpockParameterClassVisitor(Set<String> testMethodName, TestsReader testsReader) {
        this.failedTestNames = testMethodName;
        this.testsReader = testsReader;
    }

    @Override
    public Map<String, List<String>> getResult() {
        if (!isSpec) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> map = new HashMap<>();
        spockMethodVisitor.annotationVisitor.testMethodPatterns.forEach(
            methodPattern -> {
                // Replace params in the method name with .*
                String methodPatternRegex = Arrays.stream(methodPattern.split(SPOCK_PARAM_PATTERN))
                    .map(Pattern::quote)
                    .collect(Collectors.joining(WILDCARD))
                    + WILDCARD; // For when no params in name - [iterationNum] implicitly added to end

                failedTestNames.forEach(failedTestName -> {
                    List<String> matches = map.computeIfAbsent(failedTestName, ignored -> new ArrayList<>());
                    if (methodPattern.equals(failedTestName) || failedTestName.matches(methodPatternRegex)) {
                        matches.add(methodPattern);
                    }
                });
            });
        return map;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        if (superName != null) {
            if (superName.equals("spock/lang/Specification")) {
                isSpec = true;
            } else {
                testsReader.readClass(superName.replace('/', '.'), () -> this);
            }
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return isSpec ? spockMethodVisitor : null;
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

