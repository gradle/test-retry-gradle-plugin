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

import org.gradle.testretry.AbstractFrameworkFuncTest

class JUnit5FuncTest extends AbstractFrameworkFuncTest {
    @Override
    String getLanguagePlugin() {
        return 'java'
    }

    protected String afterClassErrorTestMethodName(String gradleVersion) {
        gradleVersion == "5.0" ? "classMethod" : "executionError"
    }

    protected String beforeClassErrorTestMethodName(String gradleVersion) {
        gradleVersion == "5.0" ? "classMethod" : "initializationError"
    }

    def "handles failure in #lifecycle - exhaustive #exhaust (gradle version #gradleVersion)"(String gradleVersion, String lifecycle, boolean exhaust) {
        given:
        buildFile << """
            test.retry.maxRetries = 2
        """

        writeTestSource """
            package acme;

            class SuccessfulTests {
                @org.junit.jupiter.api.${lifecycle}
                ${lifecycle.contains('All') ? 'static ' : ''}void lifecycle() {
                    ${flakyAssert("id", exhaust ? 3 : 2)}
                }

                @org.junit.jupiter.api.Test
                void successTest() {}
            }
        """

        when:
        def runner = gradleRunner(gradleVersion as String)
        def result = exhaust ? runner.buildAndFail() : runner.build()

        then:
        if (exhaust) {
            if (lifecycle == "BeforeAll") {
                assert result.output.count("${beforeClassErrorTestMethodName(gradleVersion)} FAILED") == 3
                assert result.output.count("${beforeClassErrorTestMethodName(gradleVersion)} PASSED") == 0
                assert result.output.count('successTest() FAILED') == 0
                assert result.output.count('successTest() PASSED') == 0
            } else if (lifecycle == "BeforeEach" || lifecycle == "AfterEach") {
                assert result.output.count('initializationError FAILED') == 0
                assert result.output.count('initializationError PASSED') == 0
                assert result.output.count('successTest() FAILED') == 3
                assert result.output.count('successTest() PASSED') == 0
            } else if (lifecycle == "AfterAll") {
                assert result.output.count("${afterClassErrorTestMethodName(gradleVersion)} FAILED") == 3
                assert result.output.count("${afterClassErrorTestMethodName(gradleVersion)} PASSED") == 0
                assert result.output.count('successTest() FAILED') == 0
                assert result.output.count('successTest() PASSED') == 3
            }
        } else {
            if (lifecycle == "BeforeAll") {
                assert result.output.count("${beforeClassErrorTestMethodName(gradleVersion)} FAILED") == 2
                assert result.output.count("${beforeClassErrorTestMethodName(gradleVersion)} PASSED") == 1
                assert result.output.count('successTest() FAILED') == 0
                assert result.output.count('successTest() PASSED') == 1
            } else if (lifecycle == "BeforeEach" || lifecycle == "AfterEach") {
                assert result.output.count('initializationError FAILED') == 0
                assert result.output.count('initializationError PASSED') == 0
                assert result.output.count('successTest() FAILED') == 2
                assert result.output.count('successTest() PASSED') == 1
            } else if (lifecycle == "AfterAll") {
                assert result.output.count("${afterClassErrorTestMethodName(gradleVersion)} FAILED") == 2
                assert result.output.count("${afterClassErrorTestMethodName(gradleVersion)} PASSED") == 1
                assert result.output.count('successTest() FAILED') == 0
                assert result.output.count('successTest() PASSED') == 3
            }
        }

        where:
        [gradleVersion, lifecycle, exhaust] << GroovyCollections.combinations((Iterable) [
            GRADLE_VERSIONS_UNDER_TEST,
            ['BeforeAll', 'BeforeEach', 'AfterAll', 'AfterEach'],
            [true, false]
        ])
    }

    def "handles flaky static initializers (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme;

            class SomeTests {
                static {
                    ${flakyAssert()}
                }

                @org.junit.jupiter.api.Test
                void someTest() {}
            }
        """

        when:
        def result = gradleRunner(gradleVersion as String).build()

        then:
        result.output.count('SomeTests > someTest() PASSED') == 1
        result.output.count('SomeTests > someTest() FAILED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "handles parameterized test in super class (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme;

            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            import static org.junit.jupiter.api.Assertions.assertEquals;

            abstract class AbstractTest {
                @ParameterizedTest(name = "test(int)[{index}]")
                @ValueSource(ints = {0, 1})
                void test(int number) {
                    assertEquals(0, number);
                }
            }
        """

        writeTestSource """
            package acme;

            class ParameterTest extends AbstractTest {
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        // we can't rerun just the failed parameter
        result.output.count('test(int)[1] PASSED') == 2
        result.output.count('test(int)[2] FAILED') == 2

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "can rerun on failure in super class (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme;

            abstract class AbstractTest {
                @org.junit.jupiter.api.Test
                void parent() {
                    ${flakyAssert()}
                }
            }
        """

        writeTestSource """
            package acme;

            class FlakyTests extends AbstractTest {
                @org.junit.jupiter.api.Test
                void inherited() {
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count('parent() FAILED') == 1
        result.output.count('parent() PASSED') == 1
        result.output.count('inherited() PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "handles parameterized tests (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme;

            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            import static org.junit.jupiter.api.Assertions.assertEquals;

            class ParameterTest {
                @ParameterizedTest(name = "test(int)[{index}]")
                @ValueSource(ints = {0, 1})
                void test(int number) {
                    assertEquals(0, number);
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        // we can't rerun just the failed parameter
        result.output.count('test(int)[1] PASSED') == 2
        result.output.count('test(int)[2] FAILED') == 2

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    String reportedTestName(String testName) {
        testName + "()"
    }

    @Override
    protected String buildConfiguration() {
        return """
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter-api:5.8.0-M1'
                testImplementation 'org.junit.jupiter:junit-jupiter-params:5.8.0-M1'
                testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.8.0-M1'
            }
            test {
                useJUnitPlatform()
            }
        """
    }
}
