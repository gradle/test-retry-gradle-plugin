package org.gradle.testretry.samples

import org.gradle.exemplar.model.Sample
import org.gradle.exemplar.test.runner.SampleModifier

class ExecutableGradleWrapperModifier implements SampleModifier {

    @Override
    Sample modify(Sample sample) {
        def wrapperScript = new File(sample.projectDir, "gradlew")
        if (wrapperScript.exists()) {
            wrapperScript.setExecutable(true)
        }
        return sample
    }
}
