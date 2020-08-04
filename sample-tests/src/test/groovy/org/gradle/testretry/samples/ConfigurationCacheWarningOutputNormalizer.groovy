package org.gradle.testretry.samples

import org.gradle.samples.executor.ExecutionMetadata
import org.gradle.samples.test.normalizer.OutputNormalizer

class ConfigurationCacheWarningOutputNormalizer implements OutputNormalizer {
    @Override
    String normalize(String commandOutput, ExecutionMetadata executionMetadata) {
        return commandOutput.replaceAll(".*Test.getClassLoaderCache\\(\\) method has been deprecated.*\\R", "")
    }
}
