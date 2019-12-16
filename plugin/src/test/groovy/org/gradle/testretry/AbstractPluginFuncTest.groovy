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
package org.gradle.testretry

import org.cyberneko.html.parsers.SAXParser
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.lang.management.ManagementFactory

abstract class AbstractPluginFuncTest extends Specification {

    static final List<String> GRADLE_VERSIONS_UNDER_TEST = gradleVersionsUnderTest()

    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()

    File settingsFile

    File buildFile

    def setup() {
        settingsFile = testProjectDir.newFile('settings.gradle')
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile = testProjectDir.newFile('build.gradle')
        buildFile << baseBuildScript()

        testProjectDir.newFolder('src', 'test', 'java', 'acme')
        testProjectDir.newFolder('src', 'test', 'groovy', 'acme')

        writeTestSource """
            package acme;

            import java.nio.file.*;

            public class FlakyAssert {
                public static void flakyAssert() {
                    try {
                        Path marker = Paths.get("build/marker.file");
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

    String baseBuildScript() {
        """
            plugins {
                id 'groovy'
                id 'org.gradle.test-retry'
            }

            repositories {
                mavenCentral()
            }

            ${buildConfiguration()}

            tasks.named("test").configure {
                testLogging {
                    events "passed", "skipped", "failed"
                }
            }
        """
    }

    String baseBuildScriptWithoutPlugin() {
        baseBuildScript() - "id 'org.gradle.test-retry'"
    }

    String testLanguage() {
        'java'
    }

    protected String buildConfiguration() {
        return 'dependencies { testImplementation "junit:junit:4.12" }'
    }

    protected void successfulTest() {
        writeTestSource """
            package acme;

            public class SuccessfulTests {
                @org.junit.Test
                public void successTest() {}
            }
        """
    }

    protected void failedTest() {
        writeTestSource """
            package acme;

            import static org.junit.Assert.assertTrue;

            public class FailedTests {
                @org.junit.Test
                public void failedTest() {
                    assertTrue(false);
                }
            }
        """
    }

    protected void flakyTest() {
        writeTestSource """
            package acme;

            public class FlakyTests {
                @org.junit.Test
                public void flaky() {
                    ${flakyAssert()}
                }
            }
        """
    }


    String flakyAssert() {
        return "acme.FlakyAssert.flakyAssert();"
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    void writeTestSource(String source) {
        String className = (source =~ /class\s+(\w+)\s+/)[0][1]
        testProjectDir.newFile("src/test/${testLanguage()}/acme/${className}.${testLanguage()}") << source
    }

    GradleRunner gradleRunner(String gradleVersion) {
        GradleRunner.create()
            .withDebug(ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0)
            .withProjectDir(testProjectDir.root)
            .withArguments('test', '-s')
            .withPluginClasspath()
            .forwardOutput()
            .tap {
                gradleVersion == GradleVersion.current().toString() ? null : it.withGradleVersion(gradleVersion)
            }
    }

    def assertTestReportContains(String testClazz, String testName, int expectedSuccessCount, int expectedFailCount) {
        assertHtmlReportContains(testClazz, testName, expectedSuccessCount, expectedFailCount)
        assertXmlReportContains(testClazz, testName, expectedSuccessCount, expectedFailCount)
        true
    }

    String reportedTestName(String testName) {
        testName
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def assertHtmlReportContains(String testClazz, String testName, int expectedSuccessCount, int expectedFailCount) {
        def parser = new SAXParser()
        def page = new XmlSlurper(parser).parse(new File(testProjectDir.root, "build/reports/tests/test/classes/acme.${testClazz}.html"))
        assert page.'**'.findAll { it.name() == 'TR' && it.TD[0].text() == testName && it.TD[2].text() == 'passed' }.size() == expectedSuccessCount
        assert page.'**'.findAll { it.name() == 'TR' && it.TD[0].text() == testName && it.TD[2].text() == 'failed' }.size() == expectedFailCount
        true
    }

    def assertXmlReportContains(String testClazz, String testName, int expectedSuccessCount, int expectedFailCount) {
        def xml = new XmlSlurper().parse(new File(testProjectDir.root, "build/test-results/test/TEST-acme.${testClazz}.xml"))
        // assert summary
        xml.'**'.find { it.name() == 'testsuite' && it.@name == "acme.${testClazz}" && it.@tests == "${expectedFailCount + expectedSuccessCount}" }

        // assert details
        assert xml.'**'.findAll { it.name() == 'testcase' && it.@classname == "acme.${testClazz}" && it.@name == testName }
        assert xml.'**'.findAll { it.name() == 'testcase' && it.@classname == "acme.${testClazz}" && !it.failure.isEmpty() }.size() == expectedFailCount
        assert xml.'**'.findAll { it.name() == 'testcase' && it.@classname == "acme.${testClazz}" && it.failure.isEmpty() }.size() == expectedSuccessCount
        true
    }

    static private List<String> gradleVersionsUnderTest() {
        def explicitGradleVersions = System.getProperty('org.gradle.test.gradleVersions')
        if (explicitGradleVersions) {
            return Arrays.asList(explicitGradleVersions.split("\\|"))
        } else {
            [GradleVersion.current().toString()]
        }
    }

}
