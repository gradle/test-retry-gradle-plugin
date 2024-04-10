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
import spock.lang.Issue

class TestNGFuncTest extends AbstractFrameworkFuncTest {
    @Override
    String getLanguagePlugin() {
        return 'java'
    }

    @Override
    def setup() {
        buildFile << """
            dependencies {
                testImplementation 'org.testng:testng:7.5'
            }
        """
    }

    def "handles failure in #lifecycle (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            public class SuccessfulTests {
                @org.testng.annotations.${lifecycle}
                public ${lifecycle.contains('Class') ? 'static ' : ''}void lifecycle() {
                    ${flakyAssert()}
                }

                @org.testng.annotations.Test
                public void successTest() {}
            }
        """

        when:
        def result = gradleRunner(gradleVersion as String).build()

        then:
        with(result.output) {
            it.count('lifecycle FAILED') == 1
            it.count('lifecycle PASSED') == 1
            !it.contains("The following test methods could not be retried")
        }

        where:
        [gradleVersion, lifecycle] << GroovyCollections.combinations((Iterable) [
            GRADLE_VERSIONS_UNDER_TEST,
            ['BeforeClass', 'BeforeTest', 'BeforeMethod', 'AfterClass', 'AfterTest', 'AfterMethod']
        ])
        // Note: we don't handle BeforeSuite AfterSuite
    }

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
        with(result.output) {
            it.contains('There were failing tests. See the report')
            !it.contains('The following test methods could not be retried')
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

            import org.testng.annotations.*;

            import static org.testng.AssertJUnit.assertEquals;

            abstract class AbstractTest {
                @DataProvider(name = "parameters")
                public Object[] createParameters() {
                    return new Object[]{0, 1};
                }

                @Test(dataProvider = "parameters")
                public void test(int number) {
                    assertEquals(0, number);
                }
            }
        """

        writeJavaTestSource """
            package acme;

            public class ParameterTest extends AbstractTest {
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        // we can't rerun just the failed parameter
        with(result.output) {
            it.count('test[0](0) PASSED') == 2
            it.count('test[1](1) FAILED') == 2
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
                @org.testng.annotations.Test
                void parent() {
                    ${flakyAssert()}
                }
            }
        """

        writeJavaTestSource """
            package acme;

            public class FlakyTests extends AbstractTest {
                @org.testng.annotations.Test
                public void inherited() {
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('parent FAILED') == 1
            it.count('parent PASSED') == 1
            it.count('inherited PASSED') == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "handles test dependencies (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            import org.testng.annotations.*;

            public class OrderedTests {
                @Test(dependsOnMethods = {"childTest"})
                public void grandchildTest() {}

                @Test(dependsOnMethods = {"parentTest"})
                public void childTest() {
                    ${flakyAssert()}
                }

                @Test
                public void parentTest() {}
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('childTest FAILED') == 1
            it.count('parentTest PASSED') == 2

            // grandchildTest gets skipped initially because flaky childTest failed, but is ran as part of the retry
            it.count('grandchildTest SKIPPED') == 1
            it.count('grandchildTest PASSED') == 1
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

            import org.testng.annotations.*;

            import static org.testng.AssertJUnit.assertEquals;

            public class ParameterTest {
                @DataProvider(name = "parameters")
                public Object[] createParameters() {
                    return new Object[]{0, 1};
                }

                @Test(dataProvider = "parameters")
                public void test(int number) {
                    assertEquals(0, number);
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        // we can't rerun just the failed parameter
        with(result.output) {
            it.count('test[0](0) PASSED') == 2
            it.count('test[1](1) FAILED') == 2
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Issue("https://github.com/gradle/test-retry-gradle-plugin/issues/66")
    def "handles parameters with #parameterRepresentation.name() toString() representation (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;

            import org.testng.annotations.*;

            import static org.testng.AssertJUnit.assertEquals;

            public class ParameterTest {
                public class Foo {
                    final int value;

                    public Foo(int value) {
                        this.value = value;
                    }

                    public String toString() {
                        return ${parameterRepresentation.representation};
                    }
                }

                @DataProvider(name = "parameters")
                public Object[] createParameters() {
                    return new Object[]{new Foo(0), new Foo(1)};
                }

                @Test(dataProvider = "parameters")
                public void test(Foo foo) {
                    assertEquals(0, foo.value);
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        // we can't rerun just the failed parameter
        with(result.output.readLines()) {
            it.findAll { line -> line.matches('.*test\\[0].* PASSED') }.size() == 2
            it.findAll { line -> line.matches('.*test\\[1].* FAILED') }.size() == 2
        }

        where:
        [gradleVersion, parameterRepresentation] << GroovyCollections.combinations((Iterable) [
            GRADLE_VERSIONS_UNDER_TEST,
            ParameterExceptionString.values()
        ])
    }

    def "uses configured test listeners for test retry (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test {
                testLogging {
                    events "standard_out"
                }

                useTestNG {
                    listeners << "acme.LoggingTestListener"
                }
                retry.maxRetries = 1
            }
        """

        writeJavaTestSource """
            package acme;

            public class SomeTests {
                @org.testng.annotations.Test
                public void someTest() {
                    ${flakyAssert()}
                }
            }
        """

        writeJavaTestSource """
            package acme;

            public class LoggingTestListener extends org.testng.TestListenerAdapter {
                @Override
                public void onTestStart(org.testng.ITestResult result) {
                    System.out.println("[LoggingTestListener] Test started: " + result.getName());
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('someTest FAILED') == 1
            it.count('someTest PASSED') == 1
        }

        and:
        result.output.count('[LoggingTestListener] Test started: someTest') == 2

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "build failed if a test has failed once but never passed (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeJavaTestSource """
            package acme;
            import org.testng.annotations.*;
            import java.nio.file.*;

            public class FlakyTests {
                @Test
                public void flakyAssumeTest() {
                   ${flakyAssert()};
                   if (${markerFileExistsCheck()}) {
                       throw new org.testng.SkipException("Skip me");
                   }
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        with(result.output) {
            it.count('flakyAssumeTest FAILED') == 1
            it.count('flakyAssumeTest SKIPPED') == 1
        }

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
                @org.testng.annotations.Test
                void a() {
                }

                @org.testng.annotations.Test
                void b() {
                    ${flakyAssert()}
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('b FAILED') == 1
            it.count('b PASSED') == 1
            it.count('a PASSED') == 2
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
                @org.testng.annotations.Test
                void a() {
                }

                @org.testng.annotations.Test
                void b() {
                    ${flakyAssert()}
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        with(result.output) {
            it.count('b FAILED') == 1
            it.count('b PASSED') == 1
            it.count('a PASSED') == 2
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Override
    protected String buildConfiguration() {
        return """
            dependencies {
                testImplementation 'org.testng:testng:7.5'
            }
            test {
                useTestNG()
            }
        """
    }

    enum ParameterExceptionString {
        EMPTY('""'),
        NULL('null'),
        MISSING('super.toString()')

        String representation

        ParameterExceptionString(String representation) {
            this.representation = representation
        }

        String getRepresentation() {
            return representation
        }
    }
}
