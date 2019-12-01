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

import java.lang.management.ManagementFactory

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class TestRetryPluginFuncTest extends Specification {
    @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
    File settingsFile
    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def "can apply plugin"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id 'java'
                id 'org.gradle.test-retry'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                testImplementation "junit:junit:4.12"
            }
            
        """
        and:
        successfulTest()
        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
            .withArguments('test')
            .build()

        then:
        println result.output
        result.task(":test").outcome == SUCCESS
    }

    def "do not re-execute successful tests"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id 'java'
                id 'org.gradle.test-retry'
            }
            repositories {
                mavenCentral()
            }
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
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
            .withArguments('test')
            .build()

        then:
        println result.output
        result.task(":test").outcome == SUCCESS
    }

    def "does not retry with all tests successful"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id 'java'
                id 'org.gradle.test-retry'
            }
            repositories {
                mavenCentral()
            }
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
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
            .withArguments('test')
            .withDebug(ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0)
            .build()

        then:
        println result.output
        result.task(":test").outcome == SUCCESS
        result.output.contains("""\
            acme.SuccessfulTest > test PASSED
    
            acme.flaky.FlakyTest > test FAILED
            
            acme.flaky.FlakyTest > test PASSED
            
            3 tests completed, 1 failed
        """.stripIndent())
    }

    def "can retry failed tests"() {
        given:
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << """
            plugins {
                id 'java'
                id 'org.gradle.test-retry'
            }
            repositories {
                mavenCentral()
            }
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
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
            .withArguments('test')
            .buildAndFail()

        then:
        println result.output
        result.task(":test").outcome == FAILED
        result.output.contains("6 tests completed, 6 failed")
    }

    def successfulTest() {
        testProjectDir.newFolder('src', 'test', 'java', 'acme', 'successful')
        def flakyTest = testProjectDir.newFile('src/test/java/acme/SuccessfulTest.java')
        flakyTest << """
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
    def failedTest() {
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

    def flakyTest() {
        testProjectDir.newFolder('src', 'test', 'java', 'acme', 'flaky')
        def flakyTest = testProjectDir.newFile('src/test/java/acme/flaky/FlakyTest.java')
        flakyTest << """
        package acme.flaky;
        
        import static org.junit.Assert.assertFalse;
        import static org.junit.Assert.assertTrue;
        import org.junit.Test;

        public class FlakyTest {
            @Test
            public void test() {
                if(new java.io.File("marker.file").exists()) {
                    assertTrue(true);
                } else {
                    writeMarkerFile();
                    assertFalse(true);
                }
            }
        
            public void writeMarkerFile() {
                try {
                    java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter("marker.file"));
                    writer.write("Marker");
                    writer.close();
                } catch(java.io.IOException ioEx) {
                 // shouldn't happen
                }
            }
        }

        """
    }
}
