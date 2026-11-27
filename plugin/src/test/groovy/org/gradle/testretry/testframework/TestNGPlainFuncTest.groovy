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

import org.gradle.util.GradleVersion
import spock.lang.Issue

import javax.annotation.Nullable

class TestNGPlainFuncTest extends BaseTestNGFuncTest {

    private static final GradleVersion GRADLE_9 = GradleVersion.version("9.0")

    @Override
    String reportedLifecycleMethodName(String gradleVersion, TestNGLifecycleType lifecycleType, String methodName) {
        methodName
    }

    @Override
    String reportedParameterizedMethodName(String gradleVersion, String methodName, String paramType, int invocationNumber, @Nullable String paramValueRepresentation) {
        "${methodName}[${invocationNumber}]${paramValueRepresentation ? "(${paramValueRepresentation})" : ""}"
    }

    @Override
    boolean reportsSuccessfulLifecycleExecutions(TestNGLifecycleType lifecycleType) {
        true
    }

    /**
     * If JUnit's TestNG engine is used, then tests won't even run and the failure is silently swallowed.
     */
    def "does not handle flaky static initializers (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            public class SomeTests {

                static {
                    ${flakyAssert()}
                }

                @org.testng.annotations.Test
                public void someTest() {}
            }
        """

        when:
        def result = gradleRunner(gradleVersion as String).buildAndFail()

        then:
        if (GradleVersion.version(gradleVersion) < GRADLE_9) {
            with(result.output) {
                it.contains('There were failing tests. See the report')
                !it.contains('The following test methods could not be retried')
            }
        } else {
            with(result.output) {
                // Gradle 9 detects this as a fatal test framework error
                it.contains('Could not complete execution for Gradle Test Executor')
                !it.contains('The following test methods could not be retried')
            }
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Issue("https://github.com/gradle/test-retry-gradle-plugin/issues/487")
    def "retries data provider test with #parameter.name() parameter representation (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            import org.testng.annotations.*;

            public class ParameterTest {
                public static class Value {
                    final int number;

                    Value(int number) {
                        this.number = number;
                    }

                    ${parameter.toStringMethod}
                }

                @DataProvider(name = "parameters")
                public Object[] createParameters() {
                    return new Object[]{new Value(0), new Value(1)};
                }

                @Test(dataProvider = "parameters")
                public void test(Value value) {
                    ${flakyAssert()}
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        // the first iteration fails once, then all iterations are rerun and pass
        with(result.output) {
            it.count(' FAILED') == 1
            it.count(' PASSED') == 3
            !it.contains('The following test methods could not be retried')
        }

        where:
        [gradleVersion, parameter] << GroovyCollections.combinations((Iterable) [
            GRADLE_VERSIONS_UNDER_TEST,
            ParameterToString.values()
        ])
    }

    enum ParameterToString {
        NESTED_PARENTHESES('public String toString() { return "Value(number=" + number + ")"; }'),
        DEFAULT_OBJECT_TO_STRING(''),
        RANDOM('public String toString() { return java.util.UUID.randomUUID().toString(); }'),
        MULTI_LINE('public String toString() { return "first line\\nsecond line " + number; }')

        final String toStringMethod

        ParameterToString(String toStringMethod) {
            this.toStringMethod = toStringMethod
        }
    }
}
