package org.gradle.testretry.samples

import org.gradle.samples.executor.ExecutionMetadata
import org.gradle.samples.test.normalizer.OutputNormalizer

class ConfigurationCacheWarningOutputNormalizer implements OutputNormalizer {
    @Override
    String normalize(String commandOutput, ExecutionMetadata executionMetadata) {
        def result = commandOutput.split("\\r?\\n").collect { line ->
            line.contains("Test.getClassLoaderCache() method has been deprecated") ? "" : line
        }
        return result.join("\n");
    }
}
