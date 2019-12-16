package org.gradle.testretry.samples;

import org.gradle.samples.executor.ExecutionMetadata;
import org.gradle.samples.test.normalizer.OutputNormalizer;

class FailedTestOutputNormalizer implements OutputNormalizer {
    @Override
    String normalize(String commandOutput, ExecutionMetadata executionMetadata) {
        def result = commandOutput.split("\\r?\\n").collect { line ->
            line.contains("See the report at: file") ?
                line.replace(executionMetadata.getTempSampleProjectDir().getCanonicalPath(), "") :
                line
        }
        return result.join("\n");
    }
}
