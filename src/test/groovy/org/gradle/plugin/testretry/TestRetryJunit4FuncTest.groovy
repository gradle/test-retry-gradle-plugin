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

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class TestRetryJunit4FuncTest extends AbstractPluginFuncTest {

    @Unroll
    def "can apply plugin (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            dependencies {
                testImplementation "junit:junit:4.12"
            }            
        """
        and:
        successfulTest()
        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.task(":test").outcome == SUCCESS

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    @Unroll
    def "do not re-execute successful tests (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            dependencies {
                testImplementation "junit:junit:4.12"
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

        where:
        gradleVersion << GRADLE_VERSIONS
    }


    @Unroll
    def "does not retry with all tests successful (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            dependencies {
                testImplementation "junit:junit:4.12"
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
        flakyTest()

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.task(":test").outcome == SUCCESS
        result.output.contains("""\
            acme.SuccessfulTest > test PASSED
            
            acme.flaky.FlakyTest > flaky FAILED
            
            acme.flaky.FlakyTest > nonFlaky PASSED
            
            acme.flaky.FlakyTest > flaky PASSED
            
            4 tests completed, 1 failed
        """.stripIndent())

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    @Unroll
    def "does handle parameterized tests (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            dependencies {
                testImplementation "junit:junit:4.12"
            }
            test {
                retry {
                    maxRetries = 2
                }
                testLogging {
                    events "passed", "skipped", "failed"
                }
            }
        """

        and:
        parameterizedTest()

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.task(":test").outcome == FAILED
        result.output.contains("""\
            acme.ParameterTest > test[0: test(0)=true] PASSED
       
            acme.ParameterTest > test[1: test(1)=false] FAILED

            acme.ParameterTest > test[1: test(1)=false] FAILED

            acme.ParameterTest > test[1: test(1)=false] FAILED
                java.lang.AssertionError at ParameterTest.java:33
                
            4 tests completed, 3 failed
        """.stripIndent())

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    @Unroll
    def "can retry failed tests (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            dependencies {
                testImplementation "junit:junit:4.12"
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
        failedTest()

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.task(":test").outcome == FAILED
        result.output.contains("6 tests completed, 6 failed")

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    private void successfulTest() {
        testProjectDir.newFolder('src', 'test', 'java', 'acme', 'successful')
        def successfulTest = testProjectDir.newFile('src/test/java/acme/SuccessfulTest.java')
        successfulTest << """
        package acme;
        
        import static org.junit.Assert.assertEquals;
        import org.junit.Test;

        public class SuccessfulTest {
            @Test
            public void test() {
                assertEquals(6, 6);
            }
        }
        """
    }

    private void parameterizedTest() {
        testProjectDir.newFolder('src', 'test', 'java', 'acme')
        def successfulTest = testProjectDir.newFile('src/test/java/acme/ParameterTest.java')
        successfulTest << """
        package acme;
        
        import static org.junit.Assert.assertTrue;

        import java.util.Arrays;
        import java.util.Collection;
        
        import org.junit.Test;
        import org.junit.runner.RunWith;
        import org.junit.runners.Parameterized;
        import org.junit.runners.Parameterized.Parameters;
        
        @RunWith(Parameterized.class)
        public class ParameterTest {
            @Parameters(name = "{index}: test({0})={1}")
            public static Iterable<Object[]> data() {
                return Arrays.asList(new Object[][] { 
                         { 0, true }, { 1, false }
                   });
            }
            
            private int input;
            private boolean expected;
            
            public ParameterTest(int input, boolean expected) {
                this.input = input;
                this.expected = expected;
            }
            
            @Test
            public void test() {
                assertTrue(expected);
            }
        }
        """
    }

    private void failedTest() {
        testProjectDir.newFolder('src', 'test', 'java', 'acme', 'failed')
        def failedTest = testProjectDir.newFile('src/test/java/acme/FailedTest.java')
        failedTest << """
        package acme;
        
        import static org.junit.Assert.assertTrue;
        import org.junit.Test;

        public class FailedTest {
            @Test
            public void test() {
                assertTrue(false);
            }
        }
        """
    }

    private void flakyTest() {
        testProjectDir.newFolder('src', 'test', 'java', 'acme', 'flaky')
        def flakyTest = testProjectDir.newFile('src/test/java/acme/flaky/FlakyTest.java')
        flakyTest << """
        package acme.flaky;
        
        import static org.junit.Assert.*;
        import java.nio.file.*;
        import org.junit.Test;

        public class FlakyTest {
            @Test
            public void nonFlaky() throws java.io.IOException {
                assertTrue(true);
            }
            
            @Test
            public void flaky() throws java.io.IOException {
                Path marker = Paths.get("marker.file");
                if(Files.exists(marker)) {
                    assertTrue(true);
                } else {
                    Files.write(marker, "mark".getBytes());
                    assertFalse(true);
                }
            }
        }
        """
    }
}
