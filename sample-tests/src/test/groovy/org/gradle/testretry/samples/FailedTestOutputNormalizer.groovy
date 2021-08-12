package org.gradle.testretry.samples;

import org.gradle.exemplar.executor.ExecutionMetadata;
import org.gradle.exemplar.test.normalizer.OutputNormalizer;

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
