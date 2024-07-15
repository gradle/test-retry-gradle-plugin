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

import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.intellij.lang.annotations.Language
import spock.lang.Issue

class TestLifecycleEventFuncTest extends AbstractPluginFuncTest {
    @Override
    String getLanguagePlugin() {
        return 'java'
    }

    @Override
    protected String buildConfiguration() {
        return """
            ${CAPTURE_EVENTS}

            dependencies {
                testImplementation("org.testng:testng:7.7.1")
                testRuntimeOnly("org.junit.support:testng-engine:1.0.4")
            }
            
            java {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(11))
                }
            }
      
            tasks.named("test").configure {
                retry {
                    maxRetries = 3
                }
                
                useJUnitPlatform {
                    includeEngines("testng")
                    excludeEngines("junit-jupiter", "junit-vintage")
                }
            }
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/29633")
    def "all internal test events are generated when error occurs while initializing test"() {
        writeJavaTestSource("""
            package org.gradle.test;

            import org.testng.Assert;
            import org.testng.annotations.*;
            
            public class SomeTest {
                @Test
                public void test_demo_1() {
                    Assert.assertTrue(true, "Test pass");
                }
            }
        """)

        // test_demo_2 will initialize successfully on the first test execution, and will fail.
        // On the retry, it will fail to initialize because it depends on SomeTest.test_demo_1,
        // which will not be included in the execution (because it passed on the first execution),
        // causing an error and no retry.
        writeJavaTestSource("""
            package org.gradle.test;

            import org.testng.Assert;
            import org.testng.annotations.*;
            
            public class SomeTest2 {
                @Test
                public void test_demo_1() {
                    Assert.assertTrue(true, "Test pass");
                }
            
                @Test(dependsOnMethods = {"test_demo_1"})
                public void test_demo_2() {
                    Assert.assertTrue(false, "Test fail");
                }
            }
        """)

        when:
        def result = gradleRunner(GradleVersion.current() as String, "test").buildAndFail()

        then:
        result.output.contains("SomeTest2 > test_demo_2 FAILED")
        result.output.contains("The following test methods could not be retried, which is unexpected.")

        and:
        result.task(":checkEvents").outcome == TaskOutcome.SUCCESS
    }

    @Language("groovy")
    private static final String CAPTURE_EVENTS = """
        import org.gradle.api.tasks.testing.TestOutputEvent
        import org.gradle.internal.event.ListenerManager
        import org.gradle.api.internal.tasks.testing.results.TestListenerInternal
        import org.gradle.api.internal.tasks.testing.report.TestResult
        import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
        import org.gradle.api.internal.tasks.testing.TestStartEvent
        import org.gradle.api.internal.tasks.testing.TestCompleteEvent
        
        def capture = new EventCapture()
        def checkEvents = tasks.register("checkEvents") {
            doLast {
                capture.events.each { testDescriptor, eventList ->
                    if (!eventList.contains(Event.STARTED)) {
                        throw new IllegalStateException("Test \$testDescriptor did not register a start event")
                    }
                    if (!eventList.contains(Event.COMPLETED)) {
                        throw new IllegalStateException("Test \$testDescriptor did not register a complete event")
                    }
                }
            }
        }
        
        tasks.named("test").configure {
            getServices().get(ListenerManager).addListener(capture)
            finalizedBy checkEvents
        }
        
        enum Event {
            STARTED, COMPLETED, OUTPUT
        }
        
        class EventCapture implements TestListenerInternal {
            def events = [:]
            
            void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
                registerEvent(testDescriptor, Event.STARTED)
            }
            
            @Override
            void completed(TestDescriptorInternal testDescriptor ,org.gradle.api.tasks.testing.TestResult testResult ,TestCompleteEvent completeEvent) {
                registerEvent(testDescriptor, Event.COMPLETED)
            }

            void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
                registerEvent(testDescriptor, Event.OUTPUT)
            }
            
            void registerEvent(TestDescriptorInternal testDescriptor, Event event) {
                events.putIfAbsent(testDescriptor, [])
                events[testDescriptor] << event
            }
        }
    """
}
