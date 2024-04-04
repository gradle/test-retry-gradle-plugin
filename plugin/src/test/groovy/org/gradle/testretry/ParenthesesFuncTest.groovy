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
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion

class ParenthesesFuncTest extends AbstractPluginFuncTest {

    @Override
    String getLanguagePlugin() {
        "org.jetbrains.kotlin.jvm' version '1.9.23"
    }

    def "should work with parentheses in test name"(String gradleVersion, String testSource) {
        given:
        setupTest()

        and:
        writeKotlinTestSource testSource

        expect:
        def result = gradleRunner(gradleVersion, "test").build()
        result.task(":test").outcome == TaskOutcome.SUCCESS

        where:
        [gradleVersion, testSource] << [
            GRADLE_VERSIONS_UNDER_TEST,
            [testWithParentheses(), parameterizedTestWithParentheses()]
        ].combinations()
    }

    private void setupTest() {
        buildFile << """
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:5.9.2'
                testImplementation 'org.junit.jupiter:junit-jupiter-params:5.9.2'
            }

            test {
                useJUnitPlatform()
                retry {
                    maxRetries = 2
                    failOnPassedAfterRetry = false
                }
            }
        """
    }

    private static String testWithParentheses() {
        """
            package acme

            import org.junit.jupiter.api.Test

            class Test1 {

                @Test
                fun `test that contains (parentheses)`() {
                    ${flakyAssert()}
                }
            }
        """
    }

    private static String parameterizedTestWithParentheses() {
        """
            package acme
            
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.Arguments;
            import org.junit.jupiter.params.provider.MethodSource;

            class Test2 {

                @ParameterizedTest
                @MethodSource("data")
                fun `test that contains (parentheses)`(a: Int, b: Int) {
                    assert(a == b)
                    ${flakyAssert()}
                }
                
                companion object {
                    @JvmStatic
                    fun data() = listOf(
                        Arguments.of(1, 1)
                    )
                }
            }
        """
    }
}
