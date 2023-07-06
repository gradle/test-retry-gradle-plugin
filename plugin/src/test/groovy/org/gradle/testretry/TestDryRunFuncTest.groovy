package org.gradle.testretry

import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion

class TestDryRunFuncTest extends AbstractGeneralPluginFuncTest {

    private static final GradleVersion GRADLE_8_2_999 = GradleVersion.version("8.2.999")

    def "emits skipped test method events if dryRun = true, retry is enabled, and TD/PTS are disabled"() {
        given:
        def gradle83OrAbove = GradleVersion.version(gradleVersion) > GRADLE_8_2_999
        setupTest(gradle83OrAbove, true)
        successfulTest()

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        gradle83OrAbove ? methodSkipped(result) : methodPassed(result)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "emits skipped test method events when --test-dry-run is used, retry is enabled, and TD/PTS are disabled"() {
        given:
        def gradle83OrAbove = GradleVersion.version(gradleVersion) > GRADLE_8_2_999
        setupTest(gradle83OrAbove, false)
        successfulTest()

        when:
        def result = gradle83OrAbove ? gradleRunner(gradleVersion,  "--test-dry-run").build() : gradleRunner(gradleVersion).build()

        then:
        gradle83OrAbove ? methodSkipped(result) : methodPassed(result)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "does not emit skipped test method events when --no-test-dry-run is used, retry is enabled, and TD/PTS are disabled"() {
        given:
        def gradle83OrAbove = GradleVersion.version(gradleVersion) > GRADLE_8_2_999
        setupTest(gradle83OrAbove, false)
        successfulTest()

        when:
        def result = gradle83OrAbove ? gradleRunner(gradleVersion,  "--no-test-dry-run").build() : gradleRunner(gradleVersion).build()

        then:
        methodPassed(result)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "does not emit skipped test method events by default, if retry is enabled and TD/PTS are disabled"() {
        given:
        def gradle83OrAbove = GradleVersion.version(gradleVersion) > GRADLE_8_2_999
        setupTest(gradle83OrAbove, false)
        successfulTest()

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        methodPassed(result)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    private void setupTest(boolean gradle83OrAbove, boolean withTestDryRun) {
        buildFile << """
            test {
                ${gradle83OrAbove && withTestDryRun ? "dryRun = true" : ""}
                retry {
                    maxRetries = 1
                }
            }
        """
    }

    private static boolean methodPassed(BuildResult result) {
        return result.output.count('PASSED') == 1
    }

    private static boolean methodSkipped(BuildResult result) {
        return result.output.count('SKIPPED') == 1
    }
}
