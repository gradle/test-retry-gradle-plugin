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
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;
import org.gradle.testretry.internal.executer.TestFrameworkTemplate;
import org.gradle.testretry.internal.executer.TestNames;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.gradle.testretry.internal.executer.framework.TestFrameworkStrategy.gradleVersionIsAtLeast;

final class Junit5TestFrameworkStrategy extends BaseJunitTestFrameworkStrategy {

    @Override
    public TestFramework createRetrying(TestFrameworkTemplate template, TestFramework testFramework, TestNames failedTests) {
        DefaultTestFilter failedTestsFilter = testFilterFor(failedTests, false, template);

        if (gradleVersionIsAtLeast("8.0")) {
            return retryTestFramework(testFramework, failedTestsFilter);
        } else {
            return retryTestFrameworkForGradleOlderThanV8_0(template, failedTestsFilter);
        }
    }

    private static TestFramework retryTestFramework(TestFramework testFramework, DefaultTestFilter failedTestsFilter) {
        return testFramework.copyWithFilters(failedTestsFilter);
    }

    private static TestFramework retryTestFrameworkForGradleOlderThanV8_0(TestFrameworkTemplate template, DefaultTestFilter failedTestsFilter) {
        JUnitPlatformTestFramework retryTestFramework = newJUnitPlatformTestFrameworkInstanceForGradleOlderThanV8_0(template.task, failedTestsFilter);
        copyTestOptions((JUnitPlatformOptions) template.task.getTestFramework().getOptions(), retryTestFramework.getOptions());

        return retryTestFramework;
    }

    private static JUnitPlatformTestFramework newJUnitPlatformTestFrameworkInstanceForGradleOlderThanV8_0(Test task, DefaultTestFilter failedTestsFilter) {
        try {
            Class<?> jUnitPlatformTestFrameworkClass = JUnitPlatformTestFramework.class;
            Constructor<?> constructor = jUnitPlatformTestFrameworkClass.getConstructor(DefaultTestFilter.class);

            return (JUnitPlatformTestFramework) constructor.newInstance(failedTestsFilter);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static void copyTestOptions(JUnitPlatformOptions source, JUnitPlatformOptions target) {
        target.setIncludeEngines(source.getIncludeEngines());
        target.setExcludeEngines(source.getExcludeEngines());
        target.setIncludeTags(source.getIncludeTags());
        target.setExcludeTags(source.getExcludeTags());
    }
}
