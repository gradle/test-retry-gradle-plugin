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
package org.gradle.testretry.internal.visitors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.nio.CharBuffer;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.ASM7;

/**
 * Class visitor that identifies unparameterized test method names.
 */
public class SpockParameterClassVisitor extends ClassVisitor {

    private static final Set<Character> REGEX_CHARS = setOf(new char[]{'<', '(', '[', '{', '\\', '^', '-', '=', '$', '!', '|', ']', '}', ')', '?', '*', '+', '.', '>'});
    // A valid Java identifier https://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html#jls-3.8 including methods
    public static final String SPOCK_PARAM_PATTERN = "#[\\p{L}\\d$_.()&&[^#\\s]]+";
    public static final String PARAM_PLACEHOLDER = "#param";
    public static final String WILDCARD_SUFFIX = ".*";
    public static final String WILDCARD = WILDCARD_SUFFIX;

    private static Set<Character> setOf(char[] chars) {
        return Collections.unmodifiableSet(CharBuffer.wrap(chars).chars().mapToObj(ch -> (char) ch).collect(Collectors.toSet()));
    }

    private String testMethodName;
    private SpockParameterMethodVisitor spockMethodVisitor = new SpockParameterMethodVisitor();

    public SpockParameterClassVisitor(String testMethodName) {
        super(ASM7);
        this.testMethodName = testMethodName;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return spockMethodVisitor;
    }

    @Override
    public void visitEnd() {
        spockMethodVisitor.getTestMethodPatterns().stream()
            .filter(methodPattern -> {
                // detects a valid spock parameter and replace it with a wildcards http://spockframework.org/spock/docs/1.3/data_driven_testing.html#_more_on_unrolled_method_names
                String methodPatternRegex = escapeRegEx(normalizeMethodName(methodPattern)).replaceAll(PARAM_PLACEHOLDER, WILDCARD) + WILDCARD_SUFFIX;
                return methodPattern.equals(this.testMethodName) || this.testMethodName.matches(methodPatternRegex) || this.testMethodName.equals("classMethod");
            })
            .max(Comparator.comparingInt(String::length))
            .ifPresent(matchingMethod -> this.testMethodName = matchingMethod);
    }

    public String getTestMethodName() {
        return testMethodName;
    }

    public List<String> getAllTestMethods(){
        return spockMethodVisitor.getTestMethodPatterns();
    }

    private static String normalizeMethodName(String methodPattern) {
        return methodPattern.replaceAll(SPOCK_PARAM_PATTERN, PARAM_PLACEHOLDER);
    }

    private static String escapeRegEx(String aRegexFragment) {
        final StringBuilder result = new StringBuilder();

        final StringCharacterIterator iterator = new StringCharacterIterator(aRegexFragment);
        char character = iterator.current();
        while (character != CharacterIterator.DONE) {
            if (REGEX_CHARS.contains(character)) {
                result.append("\\").append(character);
            } else {
                result.append(character);
            }
            character = iterator.next();
        }
        return result.toString();
    }
}

class SpockParameterMethodVisitor extends MethodVisitor {

    private SpockFeatureMetadataAnnotationVisitor annotationVisitor = new SpockFeatureMetadataAnnotationVisitor();

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

    public List<String> getTestMethodPatterns() {
        return annotationVisitor.getTestMethodPatterns();
    }
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
class SpockFeatureMetadataAnnotationVisitor extends AnnotationVisitor {

    private List<String> testMethodPatterns = new ArrayList<>();

    public SpockFeatureMetadataAnnotationVisitor() {
        super(ASM7);
    }

    @Override
    public void visit(String name, Object value) {
        if ("name".equals(name)) {
            testMethodPatterns.add((String) value);
        }
    }

    public List<String> getTestMethodPatterns() {
        return testMethodPatterns;
    }
}
