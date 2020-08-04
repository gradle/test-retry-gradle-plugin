package org.gradle.testretry.samples;

import org.gradle.samples.test.normalizer.FileSeparatorOutputNormalizer;
import org.gradle.samples.test.normalizer.GradleOutputNormalizer;
import org.gradle.samples.test.runner.GradleSamplesRunner;
import org.gradle.samples.test.runner.SamplesOutputNormalizers;
import org.gradle.samples.test.runner.SamplesRoot;
import org.junit.runner.RunWith;

@RunWith(GradleSamplesRunner.class)
@SamplesRoot("build/samples")
@SamplesOutputNormalizers({FileSeparatorOutputNormalizer.class, GradleOutputNormalizer.class, FailedTestOutputNormalizer.class, ConfigurationCacheWarningOutputNormalizer.class, PlaceholderAssertionErrorOutputNormalizer.class})
public class SampleTests {

}
