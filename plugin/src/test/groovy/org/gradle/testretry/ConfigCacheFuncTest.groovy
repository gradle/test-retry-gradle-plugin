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

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll


class ConfigCacheFuncTest extends AbstractGeneralPluginFuncTest {
    @Unroll
    def "compatible with configuration cache when tests pass (gradle version #gradleVersion)"() {
        when:
        buildFile << """
            test.retry.maxRetries = 1
        """

        successfulTest()

        then:
        def result = gradleRunner(gradleVersion).build()

        and:
        result.output.count('PASSED') == 1

        where:
        gradleVersion << CONFIG_CACHE_GRADLE_VERSIONS
    }

    @Unroll
    def "compatible with configuration cache when failed tests are retried (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        flakyTest()

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count('PASSED') == 1
        result.output.count('FAILED') == 1

        assertTestReportContains("FlakyTests", reportedTestName("flaky"), 1, 1)

        where:
        gradleVersion << CONFIG_CACHE_GRADLE_VERSIONS
    }

    @Override
    GradleRunner gradleRunner(String gradleVersion, String... arguments = ['test', '-s', '--configuration-cache']) {
        return super.gradleRunner(gradleVersion, arguments)
    }

    @Override
    String getLanguagePlugin() {
        'java'
    }
}
