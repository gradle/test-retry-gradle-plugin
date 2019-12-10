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
package org.gradle.plugins.testretry.visitors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;

import static org.objectweb.asm.Opcodes.ASM7;

public class SpockStepwiseClassVisitor extends ClassVisitor {
    private boolean stepwise = false;

    public SpockStepwiseClassVisitor() {
        super(ASM7);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if(descriptor.contains("spock/lang/Stepwise")) {
            this.stepwise = true;
        }
        return null;
    }

    public boolean isStepwise() {
        return stepwise;
    }
}
