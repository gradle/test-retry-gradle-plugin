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


import spock.lang.Unroll

class FilterFuncTest extends AbstractGeneralPluginFuncTest {

    @Unroll
    def "can filter by class include pattern (gradle version #gradleVersion)"() {
        when:
        buildFile << """
            test.retry {
                maxRetries = 2
                filters.includeClasses.add("*Flaky*")
            }
            
        """

        writeTestSource """
            package acme;

            public class FlakyTests {
                ${testAnnotation}
                public void flakyTest() {
                    ${flakyAssert()}
                }
            }
        """

        writeTestSource """
            package acme;

            public class FailedTests {
                ${testAnnotation}
                public void failedTest() {
                    throw new RuntimeException("!");
                }
            }
        """


        then:
        def result = gradleRunner(gradleVersion).buildAndFail()

        and:
        result.output.count('failedTest FAILED') == 1
        result.output.count('flakyTest FAILED') == 1
        result.output.count('flakyTest PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

}
