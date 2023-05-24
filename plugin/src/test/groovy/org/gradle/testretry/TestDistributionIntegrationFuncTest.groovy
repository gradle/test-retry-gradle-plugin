/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.testretry

import org.gradle.testkit.runner.BuildResult

class TestDistributionIntegrationFuncTest extends AbstractGeneralPluginFuncTest {

    def setup() {
        buildFile << """
            test.retry.maxRetries = 1
        """
    }

    def "is deactivated when decorated distribution extension returns true (gradle version #gradleVersion)"() {
        given:
        failedTest()
        buildFile << """
            interface TestDistributionExtension {}
            class DefaultTestDistributionExtension implements TestDistributionExtension {
                boolean shouldTestRetryPluginBeDeactivated() {
                    true
                }
            }
            test.extensions.create(TestDistributionExtension.class, "distribution", DefaultTestDistributionExtension.class)
        """

        when:
        def result = gradleRunner(gradleVersion, 'test', '--info').buildAndFail()

        then:
        assertNotRetried(result)
        result.output.contains("handled by the test-distribution plugin")

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "is deactivated when decorated distribution extension changes to true (gradle version #gradleVersion)"() {
        given:
        successfulTest() // a failing one prohibit task outputs from being cached
        buildFile << """
            interface TestDistributionExtension {}
            class DefaultTestDistributionExtension implements TestDistributionExtension {
                boolean shouldTestRetryPluginBeDeactivated() {
                    Boolean.getBoolean("shouldTestRetryPluginBeDeactivated")
                }
            }
            test.extensions.create(TestDistributionExtension.class, "distribution", DefaultTestDistributionExtension.class)
        """

        when:
        System.setProperty("shouldTestRetryPluginBeDeactivated", "${true}")
        def result = gradleRunner(gradleVersion, 'test', '--info').build()

        then:
        result.output.contains("handled by the test-distribution plugin")

        when:
        System.setProperty("shouldTestRetryPluginBeDeactivated", "${false}")
        result = gradleRunner(gradleVersion, 'test', '--info').build()

        then:
        with(result.output) {
            !contains("handled by the test-distribution plugin")
            !contains("> Task :test UP-TO-DATE")
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "is deactivated when undecorated distribution extension returns true (gradle version #gradleVersion)"() {
        given:
        failedTest()
        buildFile << """
            class TestDistributionExtension {
                boolean shouldTestRetryPluginBeDeactivated() {
                    true
                }
            }
            test.extensions.add("distribution", new TestDistributionExtension())
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        assertNotRetried(result)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "is not deactivated when distribution extension returns false (gradle version #gradleVersion)"() {
        given:
        failedTest()
        buildFile << """
            interface TestDistributionExtension {}
            class DefaultTestDistributionExtension implements TestDistributionExtension {
                boolean shouldTestRetryPluginBeDeactivated() {
                    false
                }
            }
            test.extensions.create(TestDistributionExtension.class, "distribution", DefaultTestDistributionExtension.class)
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        assertRetried(result)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "is not deactivated when distribution extension does not declare the expected method (gradle version #gradleVersion)"() {
        given:
        failedTest()
        buildFile << """
            class TestDistributionExtension {
            }
            test.extensions.add("distribution", new TestDistributionExtension())
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        assertRetried(result)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def assertRetried(BuildResult result) {
        assertRetries(result, 1)
    }

    def assertNotRetried(BuildResult result) {
        assertRetries(result, 0)
    }

    def assertRetries(BuildResult result, int retries) {
        // 1 initial + retries + 1 overall task FAILED + 1 build FAILED
        with(result.output) {
    it.count('FAILED') == 1 + retries + 1 + 1
}
        assertTestReportContains("FailedTests", reportedTestName("failedTest"), 0, 1 + retries)
    }
}
