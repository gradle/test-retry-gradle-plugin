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
import java.util.stream.Collectors

import static java.util.stream.Collectors.joining
import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

abstract class AbstractPluginFuncTest extends Specification {
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
                id 'groovy'
                id 'org.gradle.test-retry'
            }
            repositories {
                mavenCentral()
            }
            ${buildConfiguration()}
        """

        testProjectDir.newFolder('src', 'test', 'java', 'acme')
        testProjectDir.newFolder('src', 'test', 'groovy', 'acme')

        writeTestSource """
            package acme;
            
            import static org.junit.Assert.*;
            import java.nio.file.*;
    
            public class FlakyAssert {
                public static void flakyAssert() {
                    try {
                        Path marker = Paths.get("marker.file");
                        if(Files.exists(marker)) {
                            assertTrue(true);
                        } else {
                            Files.write(marker, "mark".getBytes());
                            assertFalse(true);
                        }
                    } catch(java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }
            }
        """
    }

    String flakyAssert() {
        return "acme.FlakyAssert.flakyAssert();"
    }

    void writeTestSource(String source) {
        String className = (source =~ /class\s+(\w+)\s+/)[0][1]
        String language = source.contains(';') ? 'java' : 'groovy'
        testProjectDir.newFile("src/test/${language}/acme/${className}.${language}") << source
    }

    GradleRunner gradleRunner(String gradleVersion) {
        return GradleRunner.create()
            .withGradleVersion(gradleVersion)
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
            .withArguments('test')
            .withDebug(ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0)
            .forwardOutput()
    }

    abstract protected String buildConfiguration()
    abstract protected void flakyTest()
    abstract protected void successfulTest()
    abstract protected void failedTest()

    @Unroll
    def "can apply plugin (gradle version #gradleVersion)"() {
        given:
        successfulTest()

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.task(":test").outcome == SUCCESS

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    @Unroll
    def "retries failed tests (gradle version #gradleVersion)"() {
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
        failedTest()

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.task(":test").outcome == FAILED
        result.output.contains("2 tests completed, 2 failed")

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    @Unroll
    def "do not re-execute successful tests (gradle version #gradleVersion)"() {
        given:
        buildFile << """
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
    def "stop when flaky tests successful (gradle version #gradleVersion)"() {
        given:
        buildFile << """
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
        flakyTest()

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.task(":test").outcome == SUCCESS
        result.output.count('PASSED') == 1
        result.output.count('FAILED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS
    }
}
