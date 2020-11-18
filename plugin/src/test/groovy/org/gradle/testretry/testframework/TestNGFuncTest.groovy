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
import org.spockframework.util.VersionNumber
import spock.lang.Issue
import spock.lang.Unroll

class TestNGFuncTest extends AbstractFrameworkFuncTest {
    @Override
    String getLanguagePlugin() {
        return 'java'
    }

    @Override
    String getTestAnnotation() {
        return "@org.testng.annotations.Test"
    }

    // TestNG only works with config cache starting with 6.7
    boolean isFrameworkSupportedWithConfigCache(String gradleVersion) {
        // We use VersionNumber here so that we can match 6.7 nightlies
        return VersionNumber.parse(gradleVersion).with {
            it.major > 6 || (it.major == 6 && it.minor >= 7)
        }
    }

    @Unroll
    def "handles failure in #lifecycle (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
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
        def result = gradleRunner(gradleVersion as String).buildAndFail()

        then:
        result.output.count('lifecycle FAILED') >= 1
        result.output.contains("org.gradle.test-retry was unable to retry")
        result.output.contains("acme.SuccessfulTests#lifecycle")

        where:
        [gradleVersion, lifecycle] << GroovyCollections.combinations((Iterable) [
            GRADLE_VERSIONS_UNDER_TEST,
            ['BeforeClass', 'BeforeTest', 'AfterClass', 'AfterTest', 'BeforeSuite', 'AfterSuite']
        ])
    }

    @Unroll
    def "handles flaky static initializers (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
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
        result.output.contains('There were failing tests. See the report')
        !result.output.contains('org.gradle.test-retry was unable to retry the following test methods')

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "handles parameterized test in super class (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
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

        writeTestSource """
            package acme;

            public class ParameterTest extends AbstractTest {
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        // we can't rerun just the failed parameter
        result.output.count('test[0](0) PASSED') == 2
        result.output.count('test[1](1) FAILED') == 2

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "can rerun on failure in super class (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme;

            abstract class AbstractTest {
                @org.testng.annotations.Test
                void parent() {
                    ${flakyAssert()}
                }
            }
        """

        writeTestSource """
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
        result.output.count('parent FAILED') == 1
        result.output.count('parent PASSED') == 1
        result.output.count('inherited PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "handles test dependencies (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
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
        result.output.count('childTest FAILED') == 1
        result.output.count('parentTest PASSED') == 2

        // grandchildTest gets skipped initially because flaky childTest failed, but is ran as part of the retry
        result.output.count('grandchildTest SKIPPED') == 1
        result.output.count('grandchildTest PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "handles parameterized tests (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
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
        result.output.count('test[0](0) PASSED') == 2
        result.output.count('test[1](1) FAILED') == 2

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    @Issue("https://github.com/gradle/test-retry-gradle-plugin/issues/66")
    def "handles parameters with #parameterRepresentation.name() toString() representation (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
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
        result.output.readLines().findAll { line -> line.matches('.*test\\[0].* PASSED') }.size() == 2
        result.output.readLines().findAll { line -> line.matches('.*test\\[1].* FAILED') }.size() == 2

        where:
        [gradleVersion, parameterRepresentation] << GroovyCollections.combinations((Iterable) [
            GRADLE_VERSIONS_UNDER_TEST,
            ParameterExceptionString.values()
        ])
    }

    @Unroll
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

        writeTestSource """
            package acme;

            public class SomeTests {
                @org.testng.annotations.Test
                public void someTest() {
                    ${flakyAssert()}
                }
            }
        """

        writeTestSource """
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
        result.output.count('someTest FAILED') == 1
        result.output.count('someTest PASSED') == 1

        and:
        result.output.count('[LoggingTestListener] Test started: someTest') == 2

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "build failed if a test has failed once but never passed (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
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
        result.output.count('flakyAssumeTest FAILED') == 1
        result.output.count('flakyAssumeTest SKIPPED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }


    @Override
    protected String buildConfiguration() {
        return """
            dependencies {
                testImplementation 'org.testng:testng:7.0.0'
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

        String representation;

        ParameterExceptionString(String representation) {
            this.representation = representation;
        }

        String getRepresentation() {
            return representation
        }
    }

}
