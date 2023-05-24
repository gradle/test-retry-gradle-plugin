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

        writeJavaTestSource """
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
                with(result.output) {
                    it.count("${beforeClassErrorTestMethodName(gradleVersion)} FAILED") == 3
                    it.count("${beforeClassErrorTestMethodName(gradleVersion)} PASSED") == 0
                    it.count('successTest() FAILED') == 0
                    it.count('successTest() PASSED') == 0
                }
            } else if (lifecycle == "BeforeEach" || lifecycle == "AfterEach") {
                with(result.output) {
                    it.count('initializationError FAILED') == 0
                    it.count('initializationError PASSED') == 0
                    it.count('successTest() FAILED') == 3
                    it.count('successTest() PASSED') == 0
                }
            } else if (lifecycle == "AfterAll") {
                with(result.output) {
                    it.count("${afterClassErrorTestMethodName(gradleVersion)} FAILED") == 3
                    it.count("${afterClassErrorTestMethodName(gradleVersion)} PASSED") == 0
                    it.count('successTest() FAILED') == 0
                    it.count('successTest() PASSED') == 3
                }
            }
        } else {
            if (lifecycle == "BeforeAll") {
                with(result.output) {
                    it.count("${beforeClassErrorTestMethodName(gradleVersion)} FAILED") == 2
                    it.count("${beforeClassErrorTestMethodName(gradleVersion)} PASSED") == 1
                    it.count('successTest() FAILED') == 0
                    it.count('successTest() PASSED') == 1
                }
            } else if (lifecycle == "BeforeEach" || lifecycle == "AfterEach") {
                with(result.output) {
                    it.count('initializationError FAILED') == 0
                    it.count('initializationError PASSED') == 0
                    it.count('successTest() FAILED') == 2
                    it.count('successTest() PASSED') == 1
                }
            } else if (lifecycle == "AfterAll") {
                with(result.output) {
                    it.count("${afterClassErrorTestMethodName(gradleVersion)} FAILED") == 2
                    it.count("${afterClassErrorTestMethodName(gradleVersion)} PASSED") == 1
                    it.count('successTest() FAILED') == 0
                    it.count('successTest() PASSED') == 3
                }
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

        writeJavaTestSource """
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
        with(result.output) {
            it.count('SomeTests > someTest() PASSED') == 1
            it.count('SomeTests > someTest() FAILED') == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "handles parameterized test in super class (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
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

        writeJavaTestSource """
            package acme;

            class ParameterTest extends AbstractTest {
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        // we can't rerun just the failed parameter
        with(result.output) {
            it.count('test(int)[1] PASSED') == 2
            it.count('test(int)[2] FAILED') == 2
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "can rerun on failure in super class (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            abstract class AbstractTest {
                @org.junit.jupiter.api.Test
                void parent() {
                    ${flakyAssert()}
                }
            }
        """

        writeJavaTestSource """
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
        with(result.output) {
            it.count('parent() FAILED') == 1
            it.count('parent() PASSED') == 1
            it.count('inherited() PASSED') == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "handles parameterized tests (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
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

    def "can rerun on whole class via className (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry {
                maxRetries = 1
                classRetry {
                    includeClasses.add('*FlakyTests')
                }
            }
        """

        writeJavaTestSource """
            package acme;

            class FlakyTests {
                @org.junit.jupiter.api.Test
                void a() {
                }

                @org.junit.jupiter.api.Test
                void b() {
                    ${flakyAssert()}
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('b() FAILED') == 1
            it.count('b() PASSED') == 1
            it.count('a() PASSED') == 2
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "can rerun on whole class via annotation (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry {
                maxRetries = 1
                classRetry {
                    includeAnnotationClasses.add('*ClassRetry')
                }
            }
        """

        writeJavaTestSource """
            package acme;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE)
            @Retention(RetentionPolicy.RUNTIME)
            public @interface ClassRetry {

            }
        """

        writeJavaTestSource """
            package acme;

            @ClassRetry
            class FlakyTests {
                @org.junit.jupiter.api.Test
                void a() {
                }

                @org.junit.jupiter.api.Test
                void b() {
                    ${flakyAssert()}
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('b() FAILED') == 1
            it.count('b() PASSED') == 1
            it.count('a() PASSED') == 2
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "handles flaky setup that prevents the retries of initially failed methods (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 2
        """

        and:
        writeJavaTestSource """
            package acme;

            public class FlakySetupAndMethodTest {
                @org.junit.jupiter.api.BeforeAll
                public static void setup() {
                    ${flakyAssertPassFailPass("setup")}
                }

                @org.junit.jupiter.api.Test
                public void flakyTest() {
                    ${flakyAssert("method")}
                }

                @org.junit.jupiter.api.Test
                public void successfulTest() {
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('flakyTest() FAILED') == 1
            it.count("${beforeClassErrorTestMethodName(gradleVersion)} FAILED") == 1
            it.count("${beforeClassErrorTestMethodName(gradleVersion)} PASSED") == 1
            it.count('flakyTest() PASSED') == 1
            it.count('successfulTest() PASSED') == 2
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "handles setup failure after cleanup failure (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 2
        """

        and:
        writeJavaTestSource """
            package acme;

            public class FlakySetupAndMethodTest {
                @org.junit.jupiter.api.BeforeAll
                public static void setup() {
                    ${flakyAssertPassFailPass("setup")}
                }

                @org.junit.jupiter.api.AfterAll
                public static void cleanup() {
                    ${flakyAssert("cleanup")}
                }

                @org.junit.jupiter.api.Test
                public void flakyTest() {
                    ${flakyAssert("method")}
                }

                @org.junit.jupiter.api.Test
                public void successfulTest() {
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        def differentiatesBetweenSetupAndCleanupMethods = beforeClassErrorTestMethodName(gradleVersion) != afterClassErrorTestMethodName(gradleVersion)
        with(result.output) {
            it.count('flakyTest() FAILED') == 1
            it.count('flakyTest() PASSED') == 1
            it.count('successfulTest() PASSED') == 2

            if (differentiatesBetweenSetupAndCleanupMethods) {
                it.count("${afterClassErrorTestMethodName(gradleVersion)} FAILED") == 1
                it.count("${afterClassErrorTestMethodName(gradleVersion)} PASSED") == 1
                it.count("${beforeClassErrorTestMethodName(gradleVersion)} FAILED") == 1
                it.count("${beforeClassErrorTestMethodName(gradleVersion)} PASSED") == 1
            } else {
                it.count("${beforeClassErrorTestMethodName(gradleVersion)} FAILED") == 2
                it.count("${beforeClassErrorTestMethodName(gradleVersion)} PASSED") == 1
            }
        }

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
                testImplementation 'org.junit.jupiter:junit-jupiter-api:5.9.2'
                testImplementation 'org.junit.jupiter:junit-jupiter-params:5.9.2'
                testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.9.2'
            }
            test {
                useJUnitPlatform()
            }
        """
    }
}
