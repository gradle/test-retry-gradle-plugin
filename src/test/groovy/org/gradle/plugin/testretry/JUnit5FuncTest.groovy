package org.gradle.plugin.testretry

import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FAILED

class JUnit5FuncTest extends AbstractPluginFuncTest {
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
            
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;
            
            import static org.junit.jupiter.api.Assertions.assertEquals;
            
            class ParameterTest {
                @ParameterizedTest
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
            
            class SuccessfulTest {
                @org.junit.jupiter.api.Test
                void test() {}
            }
        """
    }

    @Override
    protected void failedTest() {
        writeTestSource """
            package acme;
            
            import static org.junit.jupiter.api.Assertions.assertTrue;
    
            class FailedTest {
                @org.junit.jupiter.api.Test
                void test() { 
                    assertTrue(false);
                }
            }
        """
    }

    @Override
    protected void flakyTest() {
        writeTestSource """
            package acme;
    
            class FlakyTest {                
                @org.junit.jupiter.api.Test
                void flaky() { 
                    ${flakyAssert()}
                }
            }
        """
    }
}
