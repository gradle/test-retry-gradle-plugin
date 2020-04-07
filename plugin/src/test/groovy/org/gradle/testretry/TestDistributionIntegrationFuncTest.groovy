/*
 * Copyright 2019 the original author or authors.
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
import spock.lang.Unroll

class TestDistributionIntegrationFuncTest extends AbstractGeneralPluginFuncTest {

    def setup() {
        buildFile << """
            test.retry.maxRetries = 1
        """
        failedTest()
    }

    @Unroll
    def "is deactivated when decorated distribution extension returns true (gradle version #gradleVersion)"() {
        given:
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
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        assertNotRetried(result)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "is deactivated when undecorated distribution extension returns true (gradle version #gradleVersion)"() {
        given:
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

    @Unroll
    def "is not deactivated when distribution extension returns false (gradle version #gradleVersion)"() {
        given:
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

    @Unroll
    def "is not deactivated when distribution extension does not declare the expected method (gradle version #gradleVersion)"() {
        given:
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
        assert result.output.count('FAILED') == 1 + retries + 1 + 1
        assertTestReportContains("FailedTests", reportedTestName("failedTest"), 0, 1 + retries)
    }

}
