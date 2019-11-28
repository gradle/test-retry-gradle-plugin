/**
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

package org.gradle.plugins.testretry;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.Task;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TestRetryPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().withType(Test.class, (Test t) -> configureTestTask(t));
    }

    private void configureTestTask(Test test) {
        RetryTestTaskExtension extension = test.getExtensions().create("retry", RetryTestTaskExtension.class);
        test.doFirst(t -> {
            replaceTestExecuter(test, extension);
        });
    }

    private void replaceTestExecuter(Test test, RetryTestTaskExtension extension) {
        try {
            Method getTestExecuter = Test.class.getDeclaredMethod("createTestExecuter");
            getTestExecuter.setAccessible(true);
            TestExecuter<JvmTestExecutionSpec> delegate = (TestExecuter<JvmTestExecutionSpec>) getTestExecuter.invoke(test);
            Method setTestExecuter = Test.class.getDeclaredMethod("setTestExecuter", TestExecuter.class);
            setTestExecuter.setAccessible(true);
            RetryTestListener retryTestListener = new RetryTestListener();
            test.addTestListener(retryTestListener);
            setTestExecuter.invoke(test, new RetryTestExecuter(delegate, test, retryTestListener, extension));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        }
    }

}
