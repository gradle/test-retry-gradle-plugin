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

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.management.ManagementFactory

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class TestRetryPluginFuncTest extends Specification {
//    static List<String> GRADLE_VERSIONS = ['5.0', '5.1', '5.1.1', '5.2', '5.2.1', '5.3', '5.3.1', '5.4', '5.4.1',
//                                    '5.5', '5.5.1', '5.6', '5.6.1', '5.6.2', '5.6.3', '5.6.4', '6.0', '6.0.1']

    static List<String> GRADLE_VERSIONS = ['5.0']//'6.0.1']

    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()
    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        settingsFile << "rootProject.name = 'hello-world'"

        buildFile = testProjectDir.newFile('build.gradle')
        buildFile << """
            plugins {
                id 'java'
                id 'org.gradle.test-retry'
            }
            repositories {
                mavenCentral()
            }
        """
    }

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
    
            acme.flaky.FlakyTest > test FAILED
            
            acme.flaky.FlakyTest > test PASSED
            
            3 tests completed, 1 failed
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

    private GradleRunner gradleRunner(String gradleVersion) {
        return GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .withArguments('test')
                .withDebug(ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0)
                .forwardOutput()
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
            public void test() throws java.io.IOException {
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
