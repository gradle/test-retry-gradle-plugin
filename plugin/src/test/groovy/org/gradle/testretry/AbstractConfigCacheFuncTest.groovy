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
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assume
import org.spockframework.util.VersionNumber
import spock.lang.Unroll


abstract class AbstractConfigCacheFuncTest extends AbstractGeneralPluginFuncTest {
    @Unroll
    def "compatible with configuration cache when tests pass (gradle version #gradleVersion)"() {
        shouldTest(gradleVersion)

        when:
        buildFile << """
            test.retry.maxRetries = 1
        """

        successfulTest()

        then:
        def result = gradleRunner(gradleVersion).build()

        and:
        result.output.count('PASSED') == 1

        when:
        result = gradleRunner(gradleVersion).build()

        then:
        assertConfigurationCacheIsReused(result, gradleVersion)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "compatible with configuration cache when failed tests are retried (gradle version #gradleVersion)"() {
        shouldTest(gradleVersion)

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

        when:
        result = gradleRunner(gradleVersion).build()

        then:
        assertConfigurationCacheIsReused(result, gradleVersion)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    void shouldTest(String gradleVersion) {
        // Configuration cache is supported after 6.6
        Assume.assumeTrue("$gradleVersion does not support configuration cache", VersionNumber.parse(gradleVersion).with {
            it.major > 6 || (it.major == 6 && it.minor >= 1)
        })
    }

    void assertConfigurationCacheIsReused(BuildResult result, String gradleVersion) {
        assert result.output.contains(getConfigurationCacheMessage(gradleVersion))
    }

    @Override
    GradleRunner gradleRunner(String gradleVersion, String... arguments = ['test', '-s', getConfigurationCacheArguments(gradleVersion)]) {
        return super.gradleRunner(gradleVersion, arguments)
    }

    @Override
    String getLanguagePlugin() {
        'java'
    }

    String getConfigurationCacheArguments(String gradleVersion) {
        def version = VersionNumber.parse(gradleVersion)
        if (version.major > 6 || (version.major == 6 && version.minor >= 6)) {
            return "--configuration-cache"
        } else if (version.major == 6 && version.minor == 5) {
            return "--configuration-cache=on"
        } else {
            return "-Dorg.gradle.unsafe.instant-execution=true"
        }
    }

    String getConfigurationCacheMessage(String gradleVersion) {
        def version = VersionNumber.parse(gradleVersion)
        if (version.major > 6 || (version.major == 6 && version.minor >= 5)) {
            return 'Reusing configuration cache.'
        } else {
            return 'Reusing instant execution cache.'
        }
    }

    @Override
    protected void successfulTest() {
        writeTestSource """
            package acme;

            public class SuccessfulTests {
                ${testAnnotation}
                public void successTest() {}
            }
        """
    }

    @Override
    protected void flakyTest() {
        writeTestSource """
            package acme;

            public class FlakyTests {
                ${testAnnotation}
                public void flaky() {
                    ${flakyAssert()}
                }
            }
        """
    }

    abstract String getTestAnnotation()
}
