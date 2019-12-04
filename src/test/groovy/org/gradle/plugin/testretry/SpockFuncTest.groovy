/*
 * Copyright 2019 Gradle, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugin.testretry

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
    def "handles unrolled tests (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test {
                retry {
                    maxRetries = 1
                }
                testLogging {
                    events "passed", "skipped", "failed"
                }
            }
        """

        and:
        writeTestSource """
            package acme
            
            class UnrollTests extends spock.lang.Specification {
//                @spock.lang.Unroll
//                def "unrolled"() {
//                    expect:
//                    result
//                    
//                    where:
//                    param << ['foo', 'bar', 'baz']
//                    result << [true, false, true]
//                }
                
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

        where:
        gradleVersion << TEST_GRADLE_VERSIONS
    }

    @Override
    protected void successfulTest() {
        writeTestSource """
            package acme
            
            class SuccessfulTests extends spock.lang.Specification {
                def "successful test"() {
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
                def "failing test"() {
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
            
            class FailedTests extends spock.lang.Specification {
                def "failing test"() {
                    expect:
                    ${flakyAssert()}
                }
            }
        """
    }
}
