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

class RetryOnClassLevelTest extends AbstractGeneralPluginFuncTest {

    def "retries all test methods on failure"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
            test.retry.retryOnClassLevel = true
        """

        writeTestSource """
            package acme;

            public class CombinedTests {
                @org.junit.Test
                public void ok() {}

                @org.junit.Test
                public void flaky() {
                    ${flakyAssert()}
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count('PASSED') == 3
        result.output.count('FAILED') == 1

        assertTestReportContains("CombinedTests", reportedTestName("flaky"), 1, 1)
        assertTestReportContains("CombinedTests", reportedTestName("ok"), 2, 0)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }
}
