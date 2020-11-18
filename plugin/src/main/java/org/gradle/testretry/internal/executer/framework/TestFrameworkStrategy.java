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
package org.gradle.testretry.internal.executer.framework;

import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.testretry.internal.executer.TestFrameworkTemplate;
import org.gradle.testretry.internal.executer.TestName;
import org.gradle.util.GradleVersion;

import java.util.Set;

public interface TestFrameworkStrategy {

    static TestFrameworkStrategy of(TestFramework testFramework) {
        if (testFramework instanceof JUnitTestFramework) {
            return new JunitTestFrameworkStrategy();
        } else if (testFramework instanceof JUnitPlatformTestFramework) {
            return new Junit5TestFrameworkStrategy();
        } else if (testFramework instanceof TestNGTestFramework) {
            return new TestNgTestFrameworkStrategy();
        } else {
            throw new UnsupportedOperationException("Unknown test framework: " + testFramework);
        }
    }

    static boolean gradleVersionIsAtLeast(String version) {
        return GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version(version)) >= 0;
    }

    boolean isSyntheticFailure(String testName);

    TestFramework createRetrying(TestFrameworkTemplate template, Set<TestName> failedTests);

    TestName getTestNameFrom(TestDescriptor descriptor);

}
