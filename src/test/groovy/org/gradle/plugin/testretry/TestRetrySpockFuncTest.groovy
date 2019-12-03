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

import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class TestRetrySpockFuncTest extends AbstractPluginFuncTest {

    @Unroll
    @Ignore
    def "can retry unrolled tests (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            dependencies {
                testImplementation "org.codehaus.groovy:groovy-all:2.5.7"
                testImplementation "org.spockframework:spock-core:1.3-groovy-2.5"
            }        
            test {
                retry {
                    maxRetries = 5
                }
                testLogging {
                    events "passed", "skipped", "failed"
                }
            }
        """
        and:
        successfulTest()
        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.task(":test").outcome == SUCCESS

        result.output.contains("""\
            acme.UnrollTests > can handle unrolled tests[0] PASSED

            acme.UnrollTests > can handle unrolled tests[1] FAILED

            acme.UnrollTests > can handle unrolled tests[2] PASSED
            
            acme.UnrollTests > can handle unrolled tests[1] FAILED

            4 tests completed, 1 failed
        """.stripIndent())
        where:
        gradleVersion << GRADLE_VERSIONS
    }

    private void successfulTest() {
        testProjectDir.newFolder('src', 'test', 'groovy', 'acme')
        def unrollTests = testProjectDir.newFile('src/test/groovy/acme/UnrollTests.groovy')
        unrollTests << """
        package acme;
        
        import spock.lang.Specification
        import spock.lang.Unroll

        public class UnrollTests extends Specification {
            
            @Unroll
            def "can handle unrolled tests"() {
                expect:
                result
                where:
                param << ['foo', 'bar', 'baz']
                result << [true, false, true]
            }
        }
        """
    }

}
