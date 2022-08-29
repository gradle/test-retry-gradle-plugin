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
package org.gradle.testretry.testframework

import org.gradle.api.internal.tasks.testing.TestFramework
import org.gradle.testretry.AbstractFrameworkFuncTest

class UnsupportedTestFrameworkFuncTest extends AbstractFrameworkFuncTest {

    def "logs warning if test framework is unsupported"(String gradleVersion) {
        given:
        buildFile << """
            test.retry.maxRetries = 2
            
            class CustomTestFramework implements $TestFramework.name {
                @Delegate 
                private final $TestFramework.name delegate

                CustomTestFramework($TestFramework.name delegate) {
                    this.delegate = delegate
                }
            }
            
            test.useTestFramework(new CustomTestFramework(test.testFramework))
        """

        successfulTest()

        when:
        def runner = gradleRunner(gradleVersion as String)
        def result = runner.build()

        then:
        result.output.contains("Test retry requested for task :test with unsupported test framework CustomTestFramework - failing tests will not be retried\n")

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }
}
