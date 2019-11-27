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
package org.gradle.plugins.testretry

import spock.lang.Unroll

class SpockFuncTest extends AbstractPluginFuncTest {
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

    @Unroll
    def "handles @Stepwise tests (gradle version #gradleVersion)"() {
        given:
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
        gradleVersion << TEST_GRADLE_VERSIONS
    }

    @Unroll
    def "handles unrolled tests (gradle version #gradleVersion)"() {
        given:
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

        where:
        gradleVersion << TEST_GRADLE_VERSIONS
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
}
