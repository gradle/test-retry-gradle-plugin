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
import org.junit.Assume
import spock.lang.Issue
import spock.lang.Unroll

class SpockFuncTest extends AbstractFrameworkFuncTest {
    @Override
    String getLanguagePlugin() {
        return 'groovy'
    }

    @Override
    String getTestAnnotation() {
        return ''
    }

    boolean isRerunsParameterizedMethods() {
        true
    }

    boolean canTargetInheritedMethods() {
        true
    }

    boolean nonParameterizedMethodsCanHaveCustomIterationNames() {
        true
    }

    protected String initializationErrorSyntheticTestMethodName(String gradleVersion) {
        "initializationError"
    }

    @Unroll
    def "handles failure in #lifecycle (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme

            class FlakySetupSpecTests extends spock.lang.Specification {
                def ${lifecycle}() {
                    ${flakyAssert()}
                }

                def successTest() {
                    expect:
                    true
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion as String).build()

        then:
        // will be >1 in the cleanupSpec case, because the test has already reported success
        // before cleanup happens
        result.output.count('successTest PASSED') >= 1

        where:
        [gradleVersion, lifecycle] << GroovyCollections.combinations((Iterable) [
            GRADLE_VERSIONS_UNDER_TEST,
            ['setup', 'setupSpec', 'cleanup', 'cleanupSpec']
        ])
    }

    @Unroll
    def "handles flaky static initializers (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme

            class SomeSpec extends spock.lang.Specification {
                static {
                    ${flakyAssert()}
                }

                def someTest() {
                    expect:
                    true
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion as String).build()

        then:
        result.output.count("SomeSpec > ${initializationErrorSyntheticTestMethodName(gradleVersion)} FAILED") == 1
        result.output.count('SomeSpec > someTest PASSED') == 1
        result.output.count('2 tests completed, 1 failed') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }


    @Unroll
    def "handles @Stepwise tests (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme

            @spock.lang.Stepwise
            class StepwiseTests extends spock.lang.Specification {
                def "parentTest"() {
                    expect:
                    true
                }

                def "childTest"() {
                    expect:
                    ${flakyAssert()}
                }

                def "grandchildTest"() {
                    expect:
                    true
                }
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

    def "only track a @Retry test method once to ensure it was re-ran successfully"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme

            class RetryTests extends spock.lang.Specification {
                @spock.lang.Retry
                def "retried"() {
                    expect:
                    false
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.output.count('retried FAILED') == 2
        !result.output.contains('unable to retry')

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "handles non-parameterized test names matching a parameterized name (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme

            class UnrollTests extends spock.lang.Specification {
                @spock.lang.Unroll
                def "test with #param"() {
                    expect:
                    true

                    where:
                    param << ['foo', 'bar']
                }

                def "test with c"() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count('test with c FAILED') == 1
        result.output.count('test with c PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "handles unrolled tests (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme

            class UnrollTests extends spock.lang.Specification {

                def passingTest() {
                    expect:
                    true
                }

                @spock.lang.Unroll
                def "unrolled"() {
                    expect:
                    result

                    where:
                    param << ['foo', 'bar', 'baz']
                    result << [true, false, true]
                }

                @spock.lang.Unroll
                def "unrolled with param #p"() {
                    expect:
                    result

                    where:
                    p << ['foo', 'bar', 'baz']
                    result << [true, false, true]
                }

                @spock.lang.Unroll
                def "unrolled with param [#param]"() {
                    expect:
                    result

                    where:
                    param << ['foo', 'bar', 'baz']
                    result << [true, false, true]
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.output.count('passingTest PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)

        result.output.count('unrolled[0] PASSED') == 2
        result.output.count('unrolled[1] FAILED') == 2
        result.output.count('unrolled[2] PASSED') == 2

        result.output.count('unrolled with param foo PASSED') == 2
        result.output.count('unrolled with param bar FAILED') == 2
        result.output.count('unrolled with param baz PASSED') == 2

        result.output.count('unrolled with param [foo] PASSED') == 2
        result.output.count('unrolled with param [bar] FAILED') == 2
        result.output.count('unrolled with param [baz] PASSED') == 2

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "handles unrolled tests with method call on param (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme

            class UnrollTests extends spock.lang.Specification {

                def passingTest() {
                    expect:
                    true
                }

                @spock.lang.Unroll
                def "unrolled with param [#param.toString().toUpperCase()]"() {
                    expect:
                    result

                    where:
                    param << ['foo', 'bar', 'baz']
                    result << [true, false, true]
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.output.count('passingTest PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)

        result.output.count('unrolled with param [FOO] PASSED') == 2
        result.output.count('unrolled with param [BAR] FAILED') == 2
        result.output.count('unrolled with param [BAZ] PASSED') == 2

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "handles unrolled tests with reserved regex chars (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme

            class UnrollTests extends spock.lang.Specification {
                def passingTest() {
                    expect:
                    true
                }

                @spock.lang.Unroll
                def "unrolled with param \\\$.*=.?<>(){}[][^\\\\w]!+- {([#param1])} {([#param2])}"() {
                    expect:
                    result

                    where:
                    param1 << ['foo', 'param_1', 'param1\$1']
                    param2 << ['foo', 'param_2', 'param2']
                    result << [false, false, false]
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.output.count('passingTest PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)

        result.output.count('unrolled with param $.*=.?<>(){}[][^\\w]!+- {([foo])} {([foo])} FAILED') == 2
        result.output.count('unrolled with param $.*=.?<>(){}[][^\\w]!+- {([param_1])} {([param_2])} FAILED') == 2
        result.output.count('unrolled with param $.*=.?<>(){}[][^\\w]!+- {([param1\$1])} {([param2])} FAILED') == 2

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "handles unrolled tests with additional test context method suffix (gradle version #gradleVersion)"() {
        given:
        Assume.assumeTrue(nonParameterizedMethodsCanHaveCustomIterationNames())
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource contextualTestAnnotation()

        writeTestSource contextualTestExtension()

        writeTestSource """
            package acme

            @spock.lang.Unroll
            @ContextualTest
            class UnrollTests extends spock.lang.Specification {

                @spock.lang.Unroll
                def passingTest() {
                    expect:
                    true
                }

                def "unrolled [#param] with additional test context"() {
                    expect:
                    result

                    where:
                    param << ['foo', 'bar', 'baz']
                    result << [false, true, false]
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.output.count('passingTest [suffix] PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)

        result.output.count('unrolled [foo] with additional test context [suffix] FAILED') == 2
        result.output.count('unrolled [bar] with additional test context [suffix] PASSED') == 2
        result.output.count('unrolled [baz] with additional test context [suffix] FAILED') == 2

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "can rerun on failure in super class with extension added suffix (gradle version #gradleVersion)"() {
        given:
        Assume.assumeTrue(canTargetInheritedMethods())
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource contextualTestAnnotation()

        writeTestSource contextualTestExtension()

        writeTestSource """
            package acme

            @ContextualTest
            abstract class AbstractTest extends spock.lang.Specification {
                def "parent"() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """

        writeTestSource """
            package acme.sub
            import acme.AbstractTest
            class InheritedTest extends AbstractTest {
                def "inherited"() {
                    expect:
                    true
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count('parent [suffix] FAILED') == 1
        result.output.count('parent [suffix] PASSED') == 1
        result.output.count('inherited [suffix] PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "can rerun on failure in super class (gradle version #gradleVersion)"() {
        given:
        Assume.assumeTrue(canTargetInheritedMethods())
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme

            abstract class AbstractTest extends spock.lang.Specification {
                def "parent"() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """

        writeTestSource """
            package acme.sub
            import acme.AbstractTest
            class InheritedTest extends AbstractTest {
                def "inherited"() {
                    expect:
                    true
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
    def 'can rerun parameterized test method in super class (gradle version #gradleVersion)'() {
        given:
        Assume.assumeTrue(canTargetInheritedMethods())
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme

            @spock.lang.Unroll
            abstract class AbstractTest extends spock.lang.Specification {

                def "unrolled [#param] parent"() {
                    expect:
                    ${flakyAssert()}

                    where:
                    param << ['foo']
                }
            }
        """

        writeTestSource """
            package acme

            class InheritedTest extends AbstractTest {

                def passingTest() {
                    expect:
                    true
                }

                def "inherited"() {
                    expect:
                    true
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count('passingTest PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)

        result.output.count('unrolled [foo] parent FAILED') == 1
        result.output.count('unrolled [foo] parent PASSED') == 1
        result.output.count('inherited PASSED') == (isRerunsParameterizedMethods() ? 1 : 2)
        result.output.count('inherited FAILED') == 0

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST

    }

    def "can rerun on failure in super super class (gradle version #gradleVersion)"() {
        given:
        Assume.assumeTrue(canTargetInheritedMethods())
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme

            abstract class A extends spock.lang.Specification {

                def "a"() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """

        writeTestSource """
            package acme

            abstract class B extends A {

                def "b"() {
                    expect:
                    false
                }
            }
        """

        writeTestSource """
            package acme

            class C extends B {

                def "c"() {
                    expect:
                    true
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.output.count('a FAILED') == 1
        result.output.count('a PASSED') == 1
        result.output.count('b FAILED') == 2
        result.output.count('c PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "can rerun parameterized test in inherited class defined in a binary (gradle version #gradleVersion)"() {
        given:
        Assume.assumeTrue(canTargetInheritedMethods())
        settingsFile << """
            include 'dep'
        """
        file("dep/build.gradle") << """
            plugins {
                id 'groovy'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation "org.codehaus.groovy:groovy-all:2.5.8"
                implementation "org.spockframework:spock-core:1.3-groovy-2.5"
            }
        """

        file("dep/src/main/groovy/acme/FlakyAssert.java") << flakyAssertClass()
        file("dep/src/main/groovy/acme/AbstractTest.groovy") << """
            package acme;
            
            class AbstractTest extends spock.lang.Specification {
                @spock.lang.Unroll
                def "unrolled [#param] parent"() {
                    expect:
                    ${flakyAssert()}

                    where:
                    param << ["foo"]
                }
            }
        """
        buildFile << """
            test.retry.maxRetries = 1

            dependencies {
                testImplementation project(":dep")
            }
        """

        writeTestSource """
            package acme

            class B extends AbstractTest {
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count('unrolled [foo] parent FAILED') == 1
        result.output.count('unrolled [foo] parent PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    @Issue("https://github.com/gradle/test-retry-gradle-plugin/issues/52")
    def "test that is skipped after failure is considered to be still failing (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 3
            test.retry.failOnPassedAfterRetry = false
        """

        writeTestSource """
            package acme
            import java.nio.file.Paths
            import java.nio.file.Files

            class Tests extends spock.lang.Specification {

                @spock.lang.IgnoreIf({${markerFileExistsCheck()}})
                def "a"() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.output.count('a FAILED') == 1
        result.output.count('a SKIPPED') == 3
        result.output.count('a PASSED') == 0
        result.output.contains('4 tests completed, 1 failed, 3 skipped')
        !result.output.contains('Please file a bug report at')

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    @Issue("https://github.com/gradle/test-retry-gradle-plugin/issues/52")
    def "test that is ignored after failure is considered to be still failing (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 3
            test.retry.failOnPassedAfterRetry = false
        """

        writeTestSource """
            package acme
            import java.nio.file.Paths
            import java.nio.file.Files

            @spock.lang.Unroll
            class FlakyTest extends spock.lang.Specification {

                @spock.lang.IgnoreIf({${markerFileExistsCheck()}})
                def "a"() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.output.count('a FAILED') == 1
        result.output.count('a SKIPPED') == 3
        result.output.count('a PASSED') == 0
        result.output.contains('4 tests completed, 1 failed, 3 skipped')
        !result.output.contains('Please file a bug report at')

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "build is successful if a test is ignored but never failed (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 2
            test.retry.failOnPassedAfterRetry = false
        """

        writeTestSource """
            package acme
            import java.nio.file.Paths
            import java.nio.file.Files

            @spock.lang.Stepwise
            class StepwiseTests extends spock.lang.Specification {
                def "parentTest"() {
                    expect:
                    ${flakyAssert()}
                }

                @spock.lang.Ignore
                def "childTest"() {
                    expect:
                    true
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count('childTest SKIPPED') == 2
        result.output.count('parentTest FAILED') == 1
        result.output.count('parentTest PASSED') == 1
        result.output.contains('4 tests completed, 1 failed, 2 skipped')
        !result.output.contains('Please file a bug report at')

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Override
    String testLanguage() {
        'groovy'
    }

    @Override
    protected String buildConfiguration() {
        return """
            dependencies {
                implementation "org.codehaus.groovy:groovy-all:2.5.8"
                testImplementation "org.spockframework:spock-core:1.3-groovy-2.5"
            }
        """
    }

    private static String contextualTestExtension() {
        """
            package acme

            import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
            import org.spockframework.runtime.model.SpecInfo

            class ContextualTestExtension extends AbstractAnnotationDrivenExtension<ContextualTest> {

                @Override
                void visitSpecAnnotation(ContextualTest annotation, SpecInfo spec) {

                    spec.features.each { feature ->
                        feature.reportIterations = true
                        def currentNameProvider = feature.iterationNameProvider
                        feature.iterationNameProvider = {
                            def defaultName = currentNameProvider != null ? currentNameProvider.getName(it) : feature.name
                            defaultName + " [suffix]"
                        }
                    }
                }
            }
        """
    }

    private static String contextualTestAnnotation() {
        """
            package acme

            import org.spockframework.runtime.extension.ExtensionAnnotation
            import java.lang.annotation.*

            @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.TYPE])
            @Inherited
            @ExtensionAnnotation(ContextualTestExtension)
            @interface ContextualTest {
            }
        """
    }

    @Override
    protected void successfulTest() {
        writeTestSource """
            package acme;

            public class SuccessfulTests extends spock.lang.Specification {
                public void successTest() {
                    expect:
                    true
                }
            }
        """
    }

    @Override
    protected void flakyTest() {
        writeTestSource """
            package acme;

            public class FlakyTests extends spock.lang.Specification {
                public void flaky() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """
    }
}
