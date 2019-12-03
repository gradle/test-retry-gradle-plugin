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

class TestRetryJUnit4FuncTest extends AbstractPluginFuncTest {
    @Override
    protected String buildConfiguration() {
        return 'dependencies { testImplementation "junit:junit:4.12" }'
    }

    @Unroll
    def "does handle parameterized tests (gradle version #gradleVersion)"() {
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

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.task(":test").outcome == FAILED
        result.output.count('test[0: test(0)=true] PASSED') == 1
        result.output.count('test[1: test(1)=false] FAILED') == 2

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    @Override
    protected void successfulTest() {
        writeTestSource """
            package acme;
            
            public class SuccessfulTest {
                @org.junit.Test
                public void test() {}
            }
        """
    }

    @Override
    protected void failedTest() {
        writeTestSource """
            package acme;
            
            import static org.junit.Assert.assertTrue;
    
            public class FailedTest {
                @org.junit.Test
                public void test() { 
                    assertTrue(false);
                }
            }
        """
    }

    @Override
    protected void flakyTest() {
        writeTestSource """
            package acme;
    
            public class FlakyTest {                
                @org.junit.Test
                public void flaky() { 
                    ${flakyAssert()}
                }
            }
        """
    }
}
