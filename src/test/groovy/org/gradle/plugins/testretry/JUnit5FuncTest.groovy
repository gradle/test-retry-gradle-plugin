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

class JUnit5FuncTest extends AbstractPluginFuncTest {

    String reportedTestName(String testName) {
        testName + "()"
    }

    @Override
    protected String buildConfiguration() {
        return """
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter-api:5.5.2'
                testImplementation 'org.junit.jupiter:junit-jupiter-params:5.5.2'
                testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.5.2'
            }
            test {
                useJUnitPlatform()
            }
        """
    }

    @Unroll
    def "handles parameterized tests (gradle version #gradleVersion)"() {
        given:
        writeTestSource """
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
        gradleVersion << TEST_GRADLE_VERSIONS
    }

    @Override
    protected void successfulTest() {
        writeTestSource """
            package acme;
            
            class SuccessfulTests {
                @org.junit.jupiter.api.Test
                void successTest() {}
            }
        """
    }

    @Override
    protected void failedTest() {
        writeTestSource """
            package acme;
            
            import static org.junit.jupiter.api.Assertions.assertTrue;
    
            class FailedTests {
                @org.junit.jupiter.api.Test
                void failedTest() { 
                    assertTrue(false);
                }
            }
        """
    }

    @Override
    protected void flakyTest() {
        writeTestSource """
            package acme;
    
            class FlakyTests {                
                @org.junit.jupiter.api.Test
                void flaky() { 
                    ${flakyAssert()}
                }
            }
        """
    }
}
