package org.gradle.testretry.samples

import org.gradle.exemplar.executor.ExecutionMetadata
import org.gradle.exemplar.test.normalizer.OutputNormalizer

/**
 This Normalizer is required to assert stable build output locally and on GitHub Action runners.
 **/
class GithubActionsRunnerOutputNormalizer implements OutputNormalizer {
    @Override
    String normalize(String commandOutput, ExecutionMetadata executionMetadata) {
        return commandOutput.replaceAll(".*gradle/actions: Writing build results.*\\R", "")
    }
}
