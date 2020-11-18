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
package org.gradle.testretry.internal.config;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.testretry.TestRetryTaskExtension;
import org.gradle.testretry.internal.executer.RetryTestExecuter;
import org.gradle.util.VersionNumber;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class TestTaskConfigurer {

    private TestTaskConfigurer() {
    }

    public static void configureTestTask(Test test, ObjectFactory objectFactory, ProviderFactory providerFactory) {
        VersionNumber gradleVersion = VersionNumber.parse(test.getProject().getGradle().getGradleVersion());

        TestRetryTaskExtension extension;
        if (supportsGeneratedAbstractTypeImplementations(gradleVersion)) {
            extension = objectFactory.newInstance(TestRetryTaskExtension.class);
        } else {
            extension = objectFactory.newInstance(DefaultTestRetryTaskExtension.class);
        }

        TestRetryTaskExtensionAdapter adapter = new TestRetryTaskExtensionAdapter(providerFactory, extension, supportsPropertyConventions(gradleVersion));

        test.getInputs().property("retry.failOnPassedAfterRetry", adapter.getFailOnPassedAfterRetryInput());

        test.getExtensions().add(TestRetryTaskExtension.class, TestRetryTaskExtension.NAME, extension);
        test.doFirst(new ConditionalTaskAction(new InitTaskAction(adapter, objectFactory)));
        test.doLast(new ConditionalTaskAction(new FinalizeTaskAction()));
    }

    private static RetryTestExecuter createRetryTestExecuter(Test task, TestRetryTaskExtensionAdapter extension, ObjectFactory objectFactory) {
        TestExecuter<JvmTestExecutionSpec> delegate = getTestExecuter(task);
        Instantiator instantiator = invoke(declaredMethod(AbstractTestTask.class, "getInstantiator"), task);
        return new RetryTestExecuter(task, extension, delegate, instantiator, objectFactory);
    }

    private static TestExecuter<JvmTestExecutionSpec> getTestExecuter(Test task) {
        return invoke(declaredMethod(Test.class, "createTestExecuter"), task);
    }

    private static void setTestExecuter(Test task, RetryTestExecuter retryTestExecuter) {
        invoke(declaredMethod(Test.class, "setTestExecuter", TestExecuter.class), task, retryTestExecuter);
    }

    private static boolean supportsGeneratedAbstractTypeImplementations(VersionNumber gradleVersion) {
        return gradleVersion.getMajor() == 5 ? gradleVersion.getMinor() >= 3 : gradleVersion.getMajor() > 5;
    }

    private static boolean supportsPropertyConventions(VersionNumber gradleVersion) {
        return gradleVersion.getMajor() == 5 ? gradleVersion.getMinor() >= 1 : gradleVersion.getMajor() > 5;
    }

    private static class ConditionalTaskAction implements Action<Task> {

        private final Action<Test> delegate;

        public ConditionalTaskAction(Action<Test> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(@NotNull Task task) {
            if (isDeactivatedByTestDistributionPlugin(task)) {
                task.getLogger().info("Test execution via the test-retry plugin is deactivated. Retries are handled by the test-distribution plugin.");
            } else {
                delegate.execute((Test) task);
            }
        }

        private boolean isDeactivatedByTestDistributionPlugin(Task task) {
            Object distributionExtension = task.getExtensions().findByName("distribution");
            if (distributionExtension == null) {
                return false;
            }
            try {
                return invoke(method(distributionExtension.getClass(), "shouldTestRetryPluginBeDeactivated"), distributionExtension);
            } catch (Exception e) {
                task.getLogger().warn("Failed to determine whether test-retry plugin should be deactivated from distribution extension", e);
                return false;
            }
        }
    }

    private static class FinalizeTaskAction implements Action<Test> {

        @Override
        public void execute(@NotNull Test task) {
            TestExecuter<JvmTestExecutionSpec> testExecuter = getTestExecuter(task);
            if (testExecuter instanceof RetryTestExecuter) {
                ((RetryTestExecuter) testExecuter).failWithNonRetriedTestsIfAny();
            } else {
                throw new IllegalStateException("Unexpected test executer: " + testExecuter);
            }
        }
    }

    private static class InitTaskAction implements Action<Test> {

        private final TestRetryTaskExtensionAdapter adapter;
        private final ObjectFactory objectFactory;

        public InitTaskAction(TestRetryTaskExtensionAdapter adapter, ObjectFactory objectFactory) {
            this.adapter = adapter;
            this.objectFactory = objectFactory;
        }

        @Override
        public void execute(@NotNull Test task) {
            RetryTestExecuter retryTestExecuter = createRetryTestExecuter(task, adapter, objectFactory);
            setTestExecuter(task, retryTestExecuter);
        }
    }

    private static Method declaredMethod(Class<?> type, String methodName, Class<?>... paramTypes) {
        try {
            return makeAccessible(type.getDeclaredMethod(methodName, paramTypes));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static Method method(Class<?> type, String methodName, Class<?>... paramTypes) {
        try {
            return makeAccessible(type.getMethod(methodName, paramTypes));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static Method makeAccessible(Method method) {
        method.setAccessible(true);
        return method;
    }

    private static <T> T invoke(Method method, Object instance, Object... args) {
        try {
            Object result = method.invoke(instance, args);
            @SuppressWarnings("unchecked") T cast = (T) result;
            return cast;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

}
