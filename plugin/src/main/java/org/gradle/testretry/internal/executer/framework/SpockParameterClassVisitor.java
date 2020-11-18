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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.ASM7;

/**
 * Class visitor that identifies unparameterized test method names.
 */
final class SpockParameterClassVisitor extends TestsReader.Visitor<SpockParameterClassVisitor.Result> {

    // A valid Java identifier https://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html#jls-3.8 including methods
    private static final String SPOCK_PARAM_PATTERN = "#[\\p{L}\\d$_.()&&[^#\\s]]+";
    private static final String WILDCARD = ".*";

    private final String failedTestMethodNameMaybeParameterized;
    private final TestsReader testsReader;
    private final SpockParameterMethodVisitor spockMethodVisitor = new SpockParameterMethodVisitor();

    private boolean foundLiteralMethod;
    private final Set<String> matchedMethodNames = new HashSet<>();

    public SpockParameterClassVisitor(String testMethodName, TestsReader testsReader) {
        this.failedTestMethodNameMaybeParameterized = testMethodName;
        this.testsReader = testsReader;
    }

    public class Result {

        public boolean isFoundLiteralMethodName() {
            return foundLiteralMethod;
        }

        public Set<String> getMatchedMethodNames() {
            return matchedMethodNames;
        }
    }

    @Override
    public Result getResult() {
        return new Result();
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

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        if (superName != null && !superName.equals("spock/lang/Specification")) {
            testsReader.readClass(superName.replace('/', '.'), () -> this);
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

