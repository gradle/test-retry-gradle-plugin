package org.gradle.testretry.samples

import org.gradle.samples.executor.ExecutionMetadata
import org.gradle.samples.test.normalizer.OutputNormalizer

class PlaceholderAssertionErrorOutputNormalizer implements OutputNormalizer {
    @Override
    String normalize(String commandOutput, ExecutionMetadata executionMetadata) {
        def result = commandOutput.split("\\r?\\n").collect { line ->
            line.contains('org.gradle.internal.serialize.PlaceholderAssertionError') ? line.replace('org.gradle.internal.serialize.PlaceholderAssertionError', 'org.opentest4j.AssertionFailedError') : line
        }
        return result.join("\n")
    }
}
