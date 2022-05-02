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

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.testretry.internal.executer.framework.TestFrameworkStrategy;
import org.gradle.testretry.internal.filter.RetryFilter;
import org.gradle.testretry.internal.testsreader.TestsReader;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED;

final class RetryTestResultProcessor implements TestResultProcessor {

    private final TestFrameworkStrategy testFrameworkStrategy;
    private final RetryFilter filter;
    private final TestsReader testsReader;
    private final TestResultProcessor delegate;

    private final int maxFailures;
    private boolean lastRetry;
    private boolean hasRetryFilteredFailures;
    private Method failureMethod;

    private final Map<Object, TestDescriptorInternal> activeDescriptorsById = new HashMap<>();

    private TestNames currentRoundFailedTests = new TestNames();
    private TestNames previousRoundFailedTests = new TestNames();

    private Object rootTestDescriptorId;

    RetryTestResultProcessor(
        TestFrameworkStrategy testFrameworkStrategy,
        RetryFilter filter,
        TestsReader testsReader,
        TestResultProcessor delegate,
        int maxFailures
    ) {
        this.testFrameworkStrategy = testFrameworkStrategy;
        this.filter = filter;
        this.testsReader = testsReader;
        this.delegate = delegate;
        this.maxFailures = maxFailures;
    }

    @Override
    public void started(TestDescriptorInternal descriptor, TestStartEvent testStartEvent) {
        if (rootTestDescriptorId == null) {
            rootTestDescriptorId = descriptor.getId();
            activeDescriptorsById.put(descriptor.getId(), descriptor);
            delegate.started(descriptor, testStartEvent);
        } else if (!descriptor.getId().equals(rootTestDescriptorId)) {
            activeDescriptorsById.put(descriptor.getId(), descriptor);
            delegate.started(descriptor, testStartEvent);
        }
    }

    @Override
    public void completed(Object testId, TestCompleteEvent testCompleteEvent) {
        if (testId.equals(rootTestDescriptorId)) {
            if (!lastRun()) {
                return;
            }
        } else {
            TestDescriptorInternal descriptor = activeDescriptorsById.remove(testId);
            if (descriptor != null && descriptor.getClassName() != null) {
                String className = descriptor.getClassName();
                String name = descriptor.getName();

                boolean failedInPreviousRound = previousRoundFailedTests.remove(className, name);
                if (failedInPreviousRound && testCompleteEvent.getResultType() == SKIPPED) {
                    currentRoundFailedTests.add(className, name);
                }

                if (isClassDescriptor(descriptor)) {
                    previousRoundFailedTests.remove(className, n -> {
                        if (testFrameworkStrategy.isLifecycleFailureTest(testsReader, className, n)) {
                            emitFakePassedEvent(descriptor, testCompleteEvent, n);
                            return true;
                        } else {
                            return false;
                        }
                    });
                }

            }
        }

        delegate.completed(testId, testCompleteEvent);
    }

    private void emitFakePassedEvent(TestDescriptorInternal parent, TestCompleteEvent parentEvent, String name) {
        Object syntheticTestId = new Object();
        TestDescriptorInternal syntheticDescriptor = new TestDescriptorImpl(syntheticTestId, parent, name);
        long timestamp = parentEvent.getEndTime();
        delegate.started(syntheticDescriptor, new TestStartEvent(timestamp, parent.getId()));
        delegate.completed(syntheticTestId, new TestCompleteEvent(timestamp));
    }

    private boolean isClassDescriptor(TestDescriptorInternal descriptor) {
        return descriptor.getClassName() != null && descriptor.getClassName().equals(descriptor.getName());
    }

    @Override
    public void output(Object testId, TestOutputEvent testOutputEvent) {
        delegate.output(testId, testOutputEvent);
    }

    @SuppressWarnings("unused")
    public void failure(Object testId, Throwable throwable) {
        // Gradle 7.6 changed the method signature from failure(Object, Throwable) to failure(Object, TestFailure).
        // To maintain compatibility with older versions, the original method needs to exist and needs to call failure()
        // on the delegate via reflection.
        failure(testId);
        try {
            Method failureMethod = lookupFailureMethod();
            failureMethod.invoke(delegate, testId, throwable);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Method lookupFailureMethod() throws ReflectiveOperationException {
        if (failureMethod == null) {
            failureMethod = delegate.getClass().getMethod("failure", Object.class, Throwable.class);
        }
        return failureMethod;
    }

    @Override
    public void failure(Object testId, TestFailure result) {
        failure(testId);
        delegate.failure(testId, result);
    }

    private void failure(Object testId) {
        final TestDescriptorInternal descriptor = activeDescriptorsById.get(testId);
        if (descriptor != null) {
            String className = descriptor.getClassName();
            if (className != null) {
                if (filter.canRetry(className)) {
                    currentRoundFailedTests.add(className, descriptor.getName());
                } else {
                    hasRetryFilteredFailures = true;
                }
            }
        }
    }

    private boolean lastRun() {
        return currentRoundFailedTests.isEmpty()
            || lastRetry
            || (maxFailures > 0 && currentRoundFailedTests.size() >= maxFailures);
    }

    public RoundResult getResult() {
        return new RoundResult(currentRoundFailedTests, previousRoundFailedTests, lastRun(), hasRetryFilteredFailures);
    }

    public void reset(boolean lastRetry) {
        if (lastRun()) {
            throw new IllegalStateException("processor has completed");
        }

        this.lastRetry = lastRetry;
        this.previousRoundFailedTests = currentRoundFailedTests;
        this.currentRoundFailedTests = new TestNames();
        this.activeDescriptorsById.clear();
    }

}
