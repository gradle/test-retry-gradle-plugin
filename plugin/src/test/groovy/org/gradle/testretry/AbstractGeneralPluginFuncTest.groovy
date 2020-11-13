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

abstract class AbstractGeneralPluginFuncTest extends AbstractPluginFuncTest {

    String getTestAnnotation() {
        return '@org.junit.Test'
    }

    String getLanguagePlugin() {
        return 'java'
    }

    protected void successfulTest() {
        writeTestSource """
            package acme;

            public class SuccessfulTests {
                ${testAnnotation}
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
                ${testAnnotation}
                public void flaky() {
                    ${flakyAssert()}
                }
            }
        """
    }
}
