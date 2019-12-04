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
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.management.ManagementFactory

abstract class AbstractPluginFuncTest extends Specification {
    static String CURRENT_GRADLE_VERSION = System.getProperty('org.gradle.test.currentGradleVersion')
    static List<String> SUPPORTED_GRADLE_VERSIONS = ['5.0', '5.1.1', '5.2.1', '5.3.1', '5.4.1',
                                           '5.5.1', '5.6.4', '6.0.1']

    static List<String> TEST_GRADLE_VERSIONS = Boolean.getBoolean("org.gradle.test.allGradleVersions").booleanValue() ? SUPPORTED_GRADLE_VERSIONS : [CURRENT_GRADLE_VERSION]

    String testLanguage() {
        'java'
    }

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
            
            import java.nio.file.*;
    
            public class FlakyAssert {
                public static void flakyAssert() {
                    try {
                        Path marker = Paths.get("marker.file");
                        if(!Files.exists(marker)) {
                            Files.write(marker, "mark".getBytes());
                            throw new RuntimeException("fail me!");
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
        testProjectDir.newFile("src/test/${testLanguage()}/acme/${className}.${testLanguage()}") << source
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
        when:
        successfulTest()

        then:
        gradleRunner(gradleVersion).build()

        where:
        gradleVersion << TEST_GRADLE_VERSIONS
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
        result.output.contains("2 tests completed, 2 failed")

        where:
        gradleVersion << TEST_GRADLE_VERSIONS
    }

    @Unroll
    def "do not re-execute successful tests (gradle version #gradleVersion)"() {
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
        successfulTest()
        failedTest()

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then: 'Only the failed test is retried a second time'
        result.output.count('PASSED') == 1

        // 2 individual tests FAILED + 1 overall task FAILED + 1 overall build FAILED
        result.output.count('FAILED') == 2 + 1 + 1

        where:
        gradleVersion << TEST_GRADLE_VERSIONS
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
        result.output.count('PASSED') == 1
        result.output.count('FAILED') == 1

        where:
        gradleVersion << TEST_GRADLE_VERSIONS
    }
}
