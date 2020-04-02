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

import org.gradle.testretry.AbstractPluginFuncTest
import spock.lang.Unroll

class SpockFuncTest extends AbstractPluginFuncTest {
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
    def "handles failing static initializers (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme

            class SomeSpec extends spock.lang.Specification {
                ${failingStaticInitializer()}

                def someTest() {
                    expect:
                    true
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion as String).buildAndFail()

        then:
        result.output.count('acme.SomeSpec > initializationError FAILED') == 1
        result.output.count('1 test completed, 1 failed') == 1

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
                @spock.lang.Unroll
                def "unrolled"() {
                    expect:
                    result

                    where:
                    param << ['foo', 'bar', 'baz']
                    result << [true, false, true]
                }

                @spock.lang.Unroll
                def "unrolled with param #param"() {
                    expect:
                    result

                    where:
                    param << ['foo', 'bar', 'baz']
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
        result.output.count('unrolled with param $.*=.?<>(){}[][^\\w]!+- {([foo])} {([foo])} FAILED') == 2
        result.output.count('unrolled with param $.*=.?<>(){}[][^\\w]!+- {([param_1])} {([param_2])} FAILED') == 2
        result.output.count('unrolled with param $.*=.?<>(){}[][^\\w]!+- {([param1\$1])} {([param2])} FAILED') == 2

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "handles unrolled tests with additional test context method suffix (gradle version #gradleVersion)"() {
        given:
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
        result.output.count('unrolled [foo] with additional test context [suffix] FAILED') == 2
        result.output.count('unrolled [bar] with additional test context [suffix] PASSED') == 2
        result.output.count('unrolled [baz] with additional test context [suffix] FAILED') == 2

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "can rerun on failure in super class (gradle version #gradleVersion)"() {
        given:
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
        result.output.count('inherited [suffix] PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def 'can rerun parameterized test method in super class (gradle version #gradleVersion)'() {
        given:
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

                def "inherited"() {
                    expect:
                    true
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count('unrolled [foo] parent FAILED') == 1
        result.output.count('unrolled [foo] parent PASSED') == 1
        result.output.count('inherited PASSED') == 1
        result.output.count('inherited FAILED') == 0

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST

    }

    @Unroll
    def "can rerun on failure in inherited class (gradle version #gradleVersion)"() {
        given:
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
                    fail
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
        testProjectDir.newFile('spock-abstract-test.jar') <<
            getClass().getResourceAsStream('/spock-abstract-test.jar').getBytes()

        buildFile << """
            test.retry.maxRetries = 1

            dependencies {
                testImplementation files('spock-abstract-test.jar')
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

    @Override
    String testLanguage() {
        'groovy'
    }

    @Override
    protected String buildConfiguration() {
        return """
            dependencies {
                testImplementation "org.codehaus.groovy:groovy-all:2.5.8"
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
}
