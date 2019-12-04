package org.gradle.plugin.testretry

import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class TestNGFuncTest extends AbstractPluginFuncTest {
    @Override
    protected String buildConfiguration() {
        return """
            dependencies {
                testImplementation 'org.testng:testng:7.0.0'
            }
            test {
                useTestNG()
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
            
            import org.testng.annotations.*;
            
            import static org.testng.AssertJUnit.assertEquals;
            
            public class ParameterTest {
                @DataProvider(name = "parameters")
                public Object[] createParameters() {
                    return new Object[]{0, 1};
                }
            
                @Test(dataProvider = "parameters")
                public void test(int number) {
                    assertEquals(0, number);
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        // we can't rerun just the failed parameter
        result.output.count('test[0](0) PASSED') == 2
        result.output.count('test[1](1) FAILED') == 2

        where:
        gradleVersion << TEST_GRADLE_VERSIONS
    }

    @Override
    protected void successfulTest() {
        writeTestSource """
            package acme;
            
            public class SuccessfulTest {
                @org.testng.annotations.Test
                public void test() {}
            }
        """
    }

    @Override
    protected void failedTest() {
        writeTestSource """
            package acme;
            
            import static org.testng.AssertJUnit.assertTrue;
    
            public class FailedTest {
                @org.testng.annotations.Test
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
                @org.testng.annotations.Test
                public void flaky() { 
                    ${flakyAssert()}
                }
            }
        """
    }
}
