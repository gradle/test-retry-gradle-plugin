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
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.junit.JUnitOptions;
import org.gradle.testretry.internal.executer.TestFrameworkTemplate;
import org.gradle.testretry.internal.executer.TestNames;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.gradle.testretry.internal.executer.framework.TestFrameworkStrategy.gradleVersionIsAtLeast;

final class JunitTestFrameworkStrategy extends BaseJunitTestFrameworkStrategy implements TestFrameworkStrategy {

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
        JUnitTestFramework retryTestFramework = newJUnitTestFrameworkInstanceForGradleOlderThanV8_0(template.task, failedTestsFilter);
        copyTestOptions((JUnitOptions) template.task.getTestFramework().getOptions(), retryTestFramework.getOptions());

        return retryTestFramework;
    }

    private static JUnitTestFramework newJUnitTestFrameworkInstanceForGradleOlderThanV8_0(Test task, DefaultTestFilter failedTestsFilter) {
        try {
            Class<?> jUnitTestFrameworkClass = JUnitTestFramework.class;
            Constructor<?> constructor = jUnitTestFrameworkClass.getConstructor(Test.class, DefaultTestFilter.class);

            return (JUnitTestFramework) constructor.newInstance(task, failedTestsFilter);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static void copyTestOptions(JUnitOptions source, JUnitOptions target) {
        target.setIncludeCategories(source.getIncludeCategories());
        target.setExcludeCategories(source.getExcludeCategories());
    }

}
