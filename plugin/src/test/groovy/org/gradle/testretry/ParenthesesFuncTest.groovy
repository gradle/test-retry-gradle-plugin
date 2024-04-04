/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion

class ParenthesesFuncTest extends AbstractPluginFuncTest {

    private static final GradleVersion GRADLE_8_3 = GradleVersion.version("8.3")
    private static final String MIN_JUPITER_VERSION_DRY_RUN = "5.10.0-RC1"
    private static final String MIN_PLATFORM_VERSION_DRY_RUN = "1.10.0-RC1"

    @Override
    String getLanguagePlugin() {
        "org.jetbrains.kotlin.jvm' version '1.9.23"
    }

    def "should work with parentheses in test name"() {
        given:
//        buildFile.delete()
//        buildFile = testProjectDir.newFile('build.gradle.kts')
        buildFile << """
            dependencies {
//                testImplementation "org.jetbrains.kotlin:kotlin-test"
                testImplementation 'org.junit.jupiter:junit-jupiter:5.9.2'
//                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            }

            test {
                useJUnitPlatform()
                retry {
                    maxRetries = 2
                    failOnPassedAfterRetry = false
                }
            }
        """

        and:
        writeKotlinTestSource """
            package acme
            
            import org.junit.jupiter.api.Test

            class DemoTest {
            
                @Test
                fun `test that does not contain parentheses`() {
                    assert(true)
                }
            
                @Test
                fun `test that contains (parentheses)`() {
                    ${flakyAssert()}
                }
            }
        """

        expect:
        def result = gradleRunner(gradleVersion, "test").build()
        !result.output.isEmpty()

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    private void setupTest(boolean gradle83OrAbove, boolean withTestDryRun) {
        setupTest(gradle83OrAbove, withTestDryRun, Optional.empty())
    }

    private void setupTest(boolean gradle83OrAbove, boolean withTestDryRun, Optional<Boolean> withSysPropDryRun) {
        buildFile << """
            test {
                useJUnitPlatform()
                ${gradle83OrAbove && withTestDryRun ? "dryRun = true" : ""}
                ${withSysPropDryRun.map { it -> gradle83OrAbove && it ? "systemProperty('junit.platform.execution.dryRun.enabled', $it)" : "" }.orElse("")}
                retry {
                    maxRetries = 1
                }
            }
        """
    }

    private void successfulJUnit5Test() {
        writeKotlinTestSource """
            package acme;

            public class SuccessfulTests {
                @org.junit.jupiter.api.Test
                public void successTest() {}
            }
        """
    }

    private static boolean methodPassed(BuildResult result) {
        return result.output.count('PASSED') == 1
    }

    private static boolean methodSkipped(BuildResult result) {
        return result.output.count('SKIPPED') == 1
    }
}
