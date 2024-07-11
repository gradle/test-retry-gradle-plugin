/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.testretry.testframework

import javax.annotation.Nullable

class TestNGViaJUnitEngineFuncTest extends BaseTestNGFuncTest {

    private static final Set<String> UNREPORTED_LIFECYCLE_METHODS = ['BeforeTest', 'AfterTest', 'AfterClass']

    def setup() {
        buildFile << """
            dependencies {
                testImplementation 'org.testng:testng:7.5'
                testRuntimeOnly 'org.junit.support:testng-engine:1.0.5'
            }
            test {
                useJUnitPlatform()
            }
        """
    }

    @Override
    String reportedLifecycleMethodName(String methodName) {
        "executionError"
    }

    @Override
    String reportedParameterizedMethodName(String methodName, String paramType, int invocationNumber, @Nullable String paramValueRepresentation) {
        "${methodName}(${paramType}) > [${invocationNumber}] ${paramValueRepresentation ?: ''}"
    }

    @Override
    boolean reportsSuccessfulLifecycleExecutions(String lifecycleMethodType) {
        !UNREPORTED_LIFECYCLE_METHODS.contains(lifecycleMethodType)
    }
}
