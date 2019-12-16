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
package org.gradle.testretry.visitors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.ASM7;

/**
 * Class visitor that identifies unparameterized test method names.
 */
public class SpockParameterClassVisitor extends ClassVisitor {
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
                    String methodPatternRegex = methodPattern.replaceAll("#\\w+", "\\\\w+");
                    return methodPattern.equals(this.testMethodName) || this.testMethodName.matches(methodPatternRegex);
                })
                .findFirst()
                .ifPresent(matchingMethod -> this.testMethodName = matchingMethod);
    }

    public String getTestMethodName() {
        return testMethodName;
    }
}

class SpockParameterMethodVisitor extends MethodVisitor {
    private SpockFeatureMetadataAnnotationVisitor annotationVisitor = new SpockFeatureMetadataAnnotationVisitor();

    public SpockParameterMethodVisitor() {
        super(ASM7);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if(descriptor.contains("org/spockframework/runtime/model/FeatureMetadata")) {
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
 *   line=15,
 *   name="unrolled with param #param",
 *   ordinal=0,
 *   blocks={...},
 *   parameterNames={"param", "result"}
 * )
 */
class SpockFeatureMetadataAnnotationVisitor extends AnnotationVisitor {
    private List<String> testMethodPatterns = new ArrayList<>();

    public SpockFeatureMetadataAnnotationVisitor() {
        super(ASM7);
    }

    @Override
    public void visit(String name, Object value) {
        if("name".equals(name)) {
            testMethodPatterns.add((String) value);
        }
    }

    public List<String> getTestMethodPatterns() {
        return testMethodPatterns;
    }
}
