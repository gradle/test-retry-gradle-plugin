package org.gradle.plugins.testretry.samples;

import org.gradle.samples.test.normalizer.FileSeparatorOutputNormalizer;
import org.gradle.samples.test.normalizer.GradleOutputNormalizer;
import org.gradle.samples.test.runner.GradleSamplesRunner;
import org.gradle.samples.test.runner.SamplesOutputNormalizers;
import org.gradle.samples.test.runner.SamplesRoot;
import org.junit.Ignore;
import org.junit.runner.RunWith;

@RunWith(GradleSamplesRunner.class)
@SamplesRoot("../samples")
@SamplesOutputNormalizers({FileSeparatorOutputNormalizer.class, GradleOutputNormalizer.class, FailedTestOutputNormalizer.class})
@Ignore
public class SampleTests {

}
