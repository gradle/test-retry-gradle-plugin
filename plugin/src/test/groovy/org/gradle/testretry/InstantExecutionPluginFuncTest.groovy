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
import org.junit.Assume
import org.spockframework.util.VersionNumber
import spock.lang.Unroll

class InstantExecutionPluginFuncTest extends AbstractGeneralPluginFuncTest {

    def instantExecutionEnable = "-Dorg.gradle.unsafe.instant-execution=true"

    @Unroll
    def "retries failed tests (gradle version #gradleVersion)"() {
        given:
        shouldTest(gradleVersion)

        buildFile << """
            test.retry.maxRetries = 1
        """

        successfulTest()
        failedTest()

        when:
        def result1 = gradleRunner(gradleVersion, instantExecutionEnable, "test").buildAndFail()

        then:
        assertResult(result1)

        when:
        gradleRunner(gradleVersion, instantExecutionEnable, "clean").build()

        and:
        def result2 = gradleRunner(gradleVersion, instantExecutionEnable, "test", "-s").buildAndFail()

        then:
        assertResult(result2)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    private void assertResult(BuildResult result) {
        result.output.count('PASSED') == 1

        // 2 individual tests FAILED + 1 overall task FAILED + 1 overall build FAILED
        result.output.count('FAILED') == 2 + 1 + 1

        assertTestReportContains("SuccessfulTests", reportedTestName("successTest"), 1, 0)
        assertTestReportContains("FailedTests", reportedTestName("failedTest"), 0, 2)
    }

    void shouldTest(String gradleVersion) {
        // Prior to 6.1 it doesn't work because instant execution can't deal with deserialises abstract types,
        // which our extension is
        Assume.assumeTrue("$gradleVersion does not support instant execution", VersionNumber.parse(gradleVersion).with {
            it.major > 6 || (it.major == 6 && it.minor >= 1)
        })
    }
}
