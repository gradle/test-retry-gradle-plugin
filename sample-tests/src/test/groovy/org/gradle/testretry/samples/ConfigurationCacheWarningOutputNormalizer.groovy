package org.gradle.testretry.samples

import org.gradle.exemplar.executor.ExecutionMetadata
import org.gradle.exemplar.test.normalizer.OutputNormalizer

class ConfigurationCacheWarningOutputNormalizer implements OutputNormalizer {
    @Override
    String normalize(String commandOutput, ExecutionMetadata executionMetadata) {
        return commandOutput.replaceAll(".*Test.getClassLoaderCache\\(\\) method has been deprecated.*\\R", "")
            .replaceAll(".*Executing Gradle on JVM versions 16 and lower has been deprecated.*\\R", "")
            .replaceAll(".*Problems report is available at.*\\R.*\\R", "")
    }
}
