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
package org.gradle.testretry.internal.config;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.testretry.TestRetryTaskExtension;
import org.gradle.testretry.internal.executer.RetryTestExecuter;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class TestTaskConfigurer {

    private final static GradleVersion GRADLE_5_1 = GradleVersion.version("5.1");
    private final static GradleVersion GRADLE_6_1 = GradleVersion.version("6.1");
    private final static GradleVersion GRADLE_8_3 = GradleVersion.version("8.3");
    private final static String GRADLE_ENTERPRISE_BASE_PACKAGE = "com.gradle.enterprise";

    private TestTaskConfigurer() {
    }

    public static void configureTestTask(Test test, ObjectFactory objectFactory, ProviderFactory providerFactory) {
        ensureThatNoRetryExtensionIsPresent(test);

        GradleVersion gradleVersion = GradleVersion.current();

        TestRetryTaskExtension extension = objectFactory.newInstance(DefaultTestRetryTaskExtension.class);

        TestRetryTaskExtensionAdapter adapter = new TestRetryTaskExtensionAdapter(providerFactory, extension, gradleVersion);

        test.getInputs().property("retry.failOnPassedAfterRetry", adapter.getFailOnPassedAfterRetryInput());

        Provider<Boolean> shouldReplaceTestExecutor =
            shouldTestRetryPluginBeDeactivated(test, objectFactory, providerFactory, gradleVersion);
        test.getInputs().property("isDeactivatedByTestDistributionPlugin", shouldReplaceTestExecutor);

        test.getExtensions().add(TestRetryTaskExtension.class, TestRetryTaskExtension.NAME, extension);

        test.doFirst(new ConditionalTaskAction(shouldReplaceTestExecutor, new InitTaskAction(adapter, objectFactory)));
        test.doLast(new ConditionalTaskAction(shouldReplaceTestExecutor, new FinalizeTaskAction()));
    }

    private static void ensureThatNoRetryExtensionIsPresent(Test testTask) {
        Object existingRetryExtension = testTask.getExtensions().findByName(TestRetryTaskExtension.NAME);

        if (existingRetryExtension != null) {
            String retryExtensionClassName = existingRetryExtension.getClass().getName();

            if (retryExtensionClassName.startsWith(GRADLE_ENTERPRISE_BASE_PACKAGE)) {
                throw new IllegalStateException(""
                    + "The Gradle Enterprise Gradle plugin is conflicting with the Test Retry Gradle plugin "
                    + "and has already added a retry extension to the test task " + testTask.getName() + ". "
                    + "Please either remove the Test Retry Gradle plugin from this project "
                    + "or disable the registration of the retry extension in the Gradle Enterprise Gradle plugin "
                    + "by specifying the system property 'gradle.enterprise.testretry.enabled' and setting it to 'false'."
                );
            } else {
                throw new IllegalStateException(""
                    + "Another plugin is conflicting with the Test Retry Gradle plugin "
                    + "and has already added a retry extension to the test task " + testTask.getName() + ". "
                    + "Please either remove the conflicting plugin or the Test Retry Gradle plugin from this test project."
                );
            }
        }
    }

    private static Provider<Boolean> shouldTestRetryPluginBeDeactivated(
        Test test,
        ObjectFactory objectFactory,
        ProviderFactory providerFactory,
        GradleVersion gradleVersion
    ) {
        Provider<Boolean> result = providerFactory.provider(() -> callShouldTestRetryPluginBeDeactivated(test) || callGetDryRun(gradleVersion, test));
        if (supportsPropertyConventions(gradleVersion)) {
            Property<Boolean> property = objectFactory.property(Boolean.class).convention(result);
            if (supportsFinalizeValueOnRead(gradleVersion)) {
                property.finalizeValueOnRead();
            }
            result = property;
        }
        return result;
    }

    public static boolean supportsPropertyConventions(GradleVersion gradleVersion) {
        return gradleVersion.compareTo(GRADLE_5_1) >= 0;
    }

    private static boolean supportsFinalizeValueOnRead(GradleVersion gradleVersion) {
        return gradleVersion.compareTo(GRADLE_6_1) >= 0;
    }

    private static boolean supportsDryRun(GradleVersion gradleVersion) {
        return gradleVersion.compareTo(GRADLE_8_3) >= 0;
    }

    private static boolean callGetDryRun(GradleVersion gradleVersion, Test task) {
        if (supportsDryRun(gradleVersion)) {
            return task.getDryRun().getOrElse(false);
        }
        return false;
    }

    private static boolean callShouldTestRetryPluginBeDeactivated(Test test) {
        Object distributionExtension = test.getExtensions().findByName("distribution");
        if (distributionExtension == null) {
            return false;
        }
        try {
            Method result = makeAccessible(distributionExtension.getClass().getMethod("shouldTestRetryPluginBeDeactivated"));
            return invoke(result, distributionExtension);
        } catch (Exception e) {
            test.getLogger().warn("Failed to determine whether test-retry plugin should be deactivated from distribution extension", e);
            return false;
        }
    }

    private static RetryTestExecuter createRetryTestExecuter(Test task, TestRetryTaskExtensionAdapter extension, ObjectFactory objectFactory) {
        TestExecuter<JvmTestExecutionSpec> delegate = getTestExecuter(task);
        Instantiator instantiator = invoke(declaredMethod(AbstractTestTask.class, "getInstantiator"), task);
        return new RetryTestExecuter(task, extension, delegate, instantiator, objectFactory, task.getTestClassesDirs().getFiles(), task.getClasspath().getFiles());
    }

    private static TestExecuter<JvmTestExecutionSpec> getTestExecuter(Test task) {
        return invoke(declaredMethod(Test.class, "createTestExecuter"), task);
    }

    private static void setTestExecuter(Test task, RetryTestExecuter retryTestExecuter) {
        invoke(declaredMethod(Test.class, "setTestExecuter", TestExecuter.class), task, retryTestExecuter);
    }

    private static class ConditionalTaskAction implements Action<Task> {

        private final Provider<Boolean> shouldReplaceTestExecutor;
        private final Action<Test> delegate;

        public ConditionalTaskAction(Provider<Boolean> shouldReplaceTestExecutor, Action<Test> delegate) {
            this.shouldReplaceTestExecutor = shouldReplaceTestExecutor;
            this.delegate = delegate;
        }

        @Override
        public void execute(@NotNull Task task) {
            if (shouldReplaceTestExecutor.get()) {
                task.getLogger().info("Test execution via the test-retry plugin is deactivated. Retries are handled by the test-distribution plugin.");
            } else {
                delegate.execute((Test) task);
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
