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
