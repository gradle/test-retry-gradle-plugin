package org.gradle.testretry.samples;

import org.gradle.exemplar.test.normalizer.FileSeparatorOutputNormalizer;
import org.gradle.exemplar.test.normalizer.GradleOutputNormalizer;
import org.gradle.exemplar.test.runner.GradleSamplesRunner;
import org.gradle.exemplar.test.runner.SamplesOutputNormalizers;
import org.gradle.exemplar.test.runner.SamplesRoot;
import org.junit.runner.RunWith;

@RunWith(GradleSamplesRunner.class)
@SamplesRoot("build/samples")
@SamplesOutputNormalizers({FileSeparatorOutputNormalizer.class, GradleOutputNormalizer.class, FailedTestOutputNormalizer.class, ConfigurationCacheWarningOutputNormalizer.class, PlaceholderAssertionErrorOutputNormalizer.class})
public class SampleTests {

}
