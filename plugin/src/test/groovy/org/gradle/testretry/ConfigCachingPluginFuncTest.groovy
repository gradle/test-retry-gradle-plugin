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
import org.gradle.util.GradleVersion
import org.junit.Assume
import org.spockframework.util.VersionNumber
import spock.lang.Unroll

class ConfigCachingPluginFuncTest extends AbstractGeneralPluginFuncTest {

    @Unroll
    def "compatible with configuration cache when tests pass (gradle version #gradleVersion)"() {
        shouldTestConfigCache(gradleVersion)

        when:
        buildFile << """
            test.retry.maxRetries = 1
        """

        successfulTest()

        then:
        def result = gradleRunnerWithConfigurationCache(gradleVersion).build()

        and:
        result.output.count('PASSED') == 1

        when:
        result = gradleRunnerWithConfigurationCache(gradleVersion).build()

        then:
        assertConfigurationCacheIsReused(result, gradleVersion)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "compatible with configuration cache when failed tests are retried (gradle version #gradleVersion)"() {
        shouldTestConfigCache(gradleVersion)

        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        flakyTest()

        when:
        def result = gradleRunnerWithConfigurationCache(gradleVersion).build()

        then:
        result.output.count('PASSED') == 1
        result.output.count('FAILED') == 1

        when:
        result = gradleRunnerWithConfigurationCache(gradleVersion).build()

        then:
        assertConfigurationCacheIsReused(result, gradleVersion)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    void shouldTestConfigCache(String gradleVersion) {
        // Configuration cache is supported after 6.1
        Assume.assumeTrue("$gradleVersion does not support configuration cache", isAtLeastGradle6_1(gradleVersion))
    }

    boolean isAtLeastGradle6_1(String gradleVersion) {
        GradleVersion.version(gradleVersion) >= GradleVersion.version("6.1")
    }

    void assertConfigurationCacheIsReused(BuildResult result, String gradleVersion) {
        assert result.output.contains(getConfigurationCacheMessage(gradleVersion))
    }

    String[] withConfigurationCacheArguments(String gradleVersion, String[] arguments) {
        String configCacheArgument
        // We need to use VersionNumber here to match 6.6 nightlies
        def version = VersionNumber.parse(gradleVersion)
        if (version.major > 6 || (version.major == 6 && version.minor >= 6)) {
            configCacheArgument = "--configuration-cache"
        } else if (version.major == 6 && version.minor == 5) {
            configCacheArgument = "--configuration-cache=on"
        } else {
            configCacheArgument = "-Dorg.gradle.unsafe.instant-execution=true"
        }
        return arguments + [configCacheArgument]
    }

    GradleRunner gradleRunnerWithConfigurationCache(String gradleVersion, String[] arguments = ['-s', 'test']) {
        return gradleRunner(gradleVersion, withConfigurationCacheArguments(gradleVersion, arguments))
    }

    String getConfigurationCacheMessage(String gradleVersion) {
        if (GradleVersion.version(gradleVersion) >= GradleVersion.version("6.5")) {
            return 'Reusing configuration cache.'
        } else {
            return 'Reusing instant execution cache.'
        }
    }
}
