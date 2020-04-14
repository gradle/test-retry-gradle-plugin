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
package org.gradle.testretry.internal.framework;

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.testretry.internal.TestName;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

abstract class BaseJunitTestFrameworkStrategy implements TestFrameworkStrategy {

    static final Set<String> ERROR_SYNTHETIC_TEST_NAMES = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            "classMethod",
            "executionError",
            "initializationError"
        ))
    );

    @Override
    public void removeSyntheticFailures(Set<TestName> nonExecutedFailedTests, TestDescriptorInternal descriptor) {
        ERROR_SYNTHETIC_TEST_NAMES.forEach(testName -> nonExecutedFailedTests.remove(new TestName(descriptor.getClassName(), testName)));
    }
}
