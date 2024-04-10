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

import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import static groovy.lang.Tuple2.tuple

class ParenthesesFuncTest extends AbstractPluginFuncTest {

    @Override
    String getLanguagePlugin() {
        "org.jetbrains.kotlin.jvm' version '1.9.23"
    }

    def "should work with parentheses in test name"(String gradleVersion, Tuple2<Closure<File>, String> scenarios) {
        given:
        def (setupTest, String testSource) = scenarios
        setupTest(buildFile)

        and:
        writeKotlinTestSource testSource

        expect:
        def result = gradleRunner(gradleVersion, "test").build()
        result.task(":test").outcome == TaskOutcome.SUCCESS

        where:
        [gradleVersion, scenarios] << [
            // Kotlin plugin compatible from 6.8 onwards
            GRADLE_VERSIONS_UNDER_TEST.findAll { GradleVersion.version(it) >= GradleVersion.version("6.8") },
            [
                tuple({ bf -> setupJunit5Test(bf) }, junit5TestWithParentheses()),
                tuple({ bf -> setupJunit5Test(bf) }, junit5ParameterizedTestWithParentheses()),
                tuple({ bf -> setupJunit4Test(bf) }, junit4TestWithJUnitParams()),
                tuple({ bf -> setupJunit4Test(bf) }, junit4TestWithJUnitParamsWithTestCaseName())
            ]
        ].combinations()
    }


    private static void setupJunit4Test(File buildFile) {
        buildFile << """
            dependencies {
                testImplementation "junit:junit:4.13.2"
                testImplementation 'pl.pragmatists:JUnitParams:1.1.1'
                testRuntimeOnly "org.junit.vintage:junit-vintage-engine:5.9.2"
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

    private static void setupJunit5Test(File buildFile) {
        buildFile << """
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:5.9.2'
                testImplementation 'org.junit.jupiter:junit-jupiter-params:5.9.2'
            }

            test {
                useJUnitPlatform()
                retry {
                    maxRetries = 2
                }
            }
        """
    }

    private static String junit4TestWithJUnitParams() {
        """
            package acme

            import junitparams.*
            import org.junit.Test
            import org.junit.runner.RunWith

            @RunWith(JUnitParamsRunner::class)
            class Test1 {

                @Test
                @Parameters("1, true")
                fun test(foo: Int, bar: Boolean) {
                    assert(foo != 0)
                    assert(bar)
                    ${flakyAssert()}
                }
            }
        """
    }

    private static String junit4TestWithJUnitParamsWithTestCaseName() {
        """
            package acme

            import junitparams.*
            import junitparams.naming.*
            import org.junit.Test
            import org.junit.runner.RunWith

            @RunWith(JUnitParamsRunner::class)
            class Test1 {

                @Test
                @Parameters("1, true")
                @TestCaseName("{method}[{index}: {method}({0})={1}]")
                fun test(foo: Int, bar: Boolean) {
                    assert(foo != 0)
                    assert(bar)
                    ${flakyAssert()}
                }
            }
        """
    }

    private static String junit5TestWithParentheses() {
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

    private static String junit5ParameterizedTestWithParentheses() {
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
