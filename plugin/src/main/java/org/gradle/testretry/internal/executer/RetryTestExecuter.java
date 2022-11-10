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
package org.gradle.testretry.internal.executer;

import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.testretry.internal.config.TestRetryTaskExtensionAccessor;
import org.gradle.testretry.internal.executer.framework.TestFrameworkStrategy;
import org.gradle.testretry.internal.filter.AnnotationInspectorImpl;
import org.gradle.testretry.internal.filter.RetryFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.testretry.internal.executer.JvmTestExecutionSpecFactory.testExecutionSpecFor;

public final class RetryTestExecuter implements TestExecuter<JvmTestExecutionSpec> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryTestExecuter.class);
    private final TestRetryTaskExtensionAccessor extension;
    private final TestExecuter<JvmTestExecutionSpec> delegate;
    private final Test testTask;
    private final TestFrameworkTemplate frameworkTemplate;

    private RoundResult lastResult;

    public RetryTestExecuter(
        Test task,
        TestRetryTaskExtensionAccessor extension,
        TestExecuter<JvmTestExecutionSpec> delegate,
        Instantiator instantiator,
        ObjectFactory objectFactory,
        Set<File> testClassesDir,
        Set<File> resolvedClasspath
    ) {
        this.extension = extension;
        this.delegate = delegate;
        this.testTask = task;
        this.frameworkTemplate = new TestFrameworkTemplate(
            testTask,
            instantiator,
            objectFactory,
            testClassesDir,
            resolvedClasspath
        );
    }

    @Override
    public void execute(JvmTestExecutionSpec spec, TestResultProcessor testResultProcessor) {
        int maxRetries = extension.getMaxRetries();
        int maxFailures = extension.getMaxFailures();
        boolean failOnPassedAfterRetry = extension.getFailOnPassedAfterRetry();

        if (maxRetries <= 0) {
            delegate.execute(spec, testResultProcessor);
            return;
        }

        TestFrameworkStrategy testFrameworkStrategy = TestFrameworkStrategy.of(spec.getTestFramework());
        if (testFrameworkStrategy == null) {
            LOGGER.warn("Test retry requested for task {} with unsupported test framework {} - failing tests will not be retried", spec.getIdentityPath(), spec.getTestFramework().getClass().getName());
            delegate.execute(spec, testResultProcessor);
            return;
        }

        RetryFilter filter = new RetryFilter(
            new AnnotationInspectorImpl(frameworkTemplate.testsReader),
            extension.getIncludeClasses(),
            extension.getIncludeAnnotationClasses(),
            extension.getExcludeClasses(),
            extension.getExcludeAnnotationClasses()
        );

        RetryTestResultProcessor retryTestResultProcessor = new RetryTestResultProcessor(
            testFrameworkStrategy,
            filter,
            frameworkTemplate.testsReader,
            testResultProcessor,
            maxFailures
        );

        int retryCount = 0;
        JvmTestExecutionSpec testExecutionSpec = spec;

        while (true) {
            delegate.execute(testExecutionSpec, retryTestResultProcessor);
            RoundResult result = retryTestResultProcessor.getResult();
            lastResult = result;

            if (extension.getSimulateNotRetryableTest() || !result.nonRetriedTests.isEmpty()) {
                // fall through to our doLast action to fail accordingly
                testTask.setIgnoreFailures(true);
                break;
            } else if (result.failedTests.isEmpty()) {
                if (retryCount > 0 && !result.hasRetryFilteredFailures && !failOnPassedAfterRetry) {
                    testTask.setIgnoreFailures(true);
                }
                break;
            } else if (result.lastRound) {
                break;
            } else {
                TestFramework retryTestFramework = testFrameworkStrategy.createRetrying(frameworkTemplate, spec.getTestFramework(), result.failedTests);
                testExecutionSpec = testExecutionSpecFor(retryTestFramework, spec);
                retryTestResultProcessor.reset(++retryCount == maxRetries);
            }
        }
    }

    public void failWithNonRetriedTestsIfAny() {
        if (extension.getSimulateNotRetryableTest() || hasNonRetriedTests()) {
            throw new IllegalStateException("org.gradle.test-retry was unable to retry the following test methods, which is unexpected. Please file a bug report at https://github.com/gradle/test-retry-gradle-plugin/issues" +
                lastResult.nonRetriedTests.stream()
                    .flatMap(entry -> entry.getValue().stream().map(methodName -> "   " + entry.getKey() + "#" + methodName))
                    .collect(Collectors.joining("\n", "\n", "\n")));
        }
    }

    private boolean hasNonRetriedTests() {
        return lastResult != null && !lastResult.nonRetriedTests.isEmpty();
    }

    @Override
    public void stopNow() {
        delegate.stopNow();
    }
}
