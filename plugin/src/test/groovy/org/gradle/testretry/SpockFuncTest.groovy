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

class SpockFuncTest extends AbstractTestFrameworkPluginFuncTest {

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
    def "handles unrolled tests with additional test context method suffix (#gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
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

        writeTestSource """
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
    def "can rerun on setupSpec failure"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme

            import java.nio.file.Files
            import java.nio.file.Path
            import java.nio.file.Paths

            class SetupFailureSpec extends spock.lang.Specification {

                void setupSpec() {
                    failInFirstRun()
                }

                def "simpleTest1"() {
                    expect:
                    true
                }

                def "simpleTest2"() {
                    expect:
                    true
                }
                ${failInFirstRunSnippet()}
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count('simpleTest1 PASSED') == 1
        result.output.count('simpleTest2 PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "can rerun on setupSpec failure with param"() {
        given:
        buildFile << """
            test {
              testLogging {
                exceptionFormat = 'full'
              }
              retry {
                maxRetries = 1
              }
            }
        """

        writeTestSource """
            package acme

            import java.nio.file.Files
            import java.nio.file.Path
            import java.nio.file.Paths

            @spock.lang.Unroll
            class SetupFailureSpec extends spock.lang.Specification {

                void setupSpec() {
                    failInFirstRun()
                }

                def "simpleTest1"() {
                    expect:
                    true
                }

                def "simpleTest2"() {
                    expect:
                    true
                }

                def "simpleTest3 [#param]"() {
                    expect:
                    result

                    where:
                    param << ['foo', 'bar', 'baz']
                    result << [true, true, true]
                }
                ${failInFirstRunSnippet()}
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count('classMethod FAILED') == 1
        result.output.count('simpleTest1 PASSED') == 1
        result.output.count('simpleTest2 PASSED') == 1
        result.output.count('simpleTest3 [foo] PASSED') == 1
        result.output.count('simpleTest3 [bar] PASSED') == 1
        result.output.count('simpleTest3 [baz] PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }


    @Unroll
    def "can rerun on setup failure"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme

            import java.nio.file.Files
            import java.nio.file.Path
            import java.nio.file.Paths

            class SetupFailureSpec extends spock.lang.Specification {

                void setup() {
                    failInFirstRun()
                }

                def "simpleTest"() {
                    expect:
                    true
                }
              ${failInFirstRunSnippet()}
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count('simpleTest FAILED') == 1
        result.output.count('simpleTest PASSED') == 1

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

    @Override
    protected void successfulTest() {
        writeTestSource """
            package acme

            class SuccessfulTests extends spock.lang.Specification {
                def successTest() {
                    expect:
                    true
                }
            }
        """
    }

    @Override
    protected void failedTest() {
        writeTestSource """
            package acme

            class FailedTests extends spock.lang.Specification {
                def failedTest() {
                    expect:
                    false
                }
            }
        """
    }

    @Override
    protected void flakyTest() {
        writeTestSource """
            package acme

            class FlakyTests extends spock.lang.Specification {
                def flaky() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """
    }

    private static String failInFirstRunSnippet() {
        """
        private static void failInFirstRun() {
            try {
                Path marker = Paths.get("marker.file");
                if (!Files.exists(marker)) {
                    Files.write(marker, "mark".getBytes());
                    throw new RuntimeException("fail me!");
                }
                Files.write(marker, "again".getBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        """
    }
}
