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
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.testretry.internal.executer.framework.TestFrameworkStrategy;

import java.util.HashMap;
import java.util.Map;

import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED;

final class RetryTestResultProcessor implements TestResultProcessor {

    private final TestFrameworkStrategy testFrameworkStrategy;
    private final TestResultProcessor delegate;

    private final int maxFailures;
    private boolean lastRetry;

    private final Map<Object, TestDescriptorInternal> activeDescriptorsById = new HashMap<>();

    private TestNames currentRoundFailedTests = new TestNames();
    private TestNames previousRoundFailedTests = new TestNames();

    private Object rootTestDescriptorId;

    RetryTestResultProcessor(TestFrameworkStrategy testFrameworkStrategy, TestResultProcessor delegate, int maxFailures) {
        this.testFrameworkStrategy = testFrameworkStrategy;
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
            if (descriptor.getClassName() != null && descriptor.getClassName().equals(descriptor.getName())) {
                previousRoundFailedTests.remove(descriptor.getClassName(), testFrameworkStrategy::isSyntheticFailure);
            }
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
                TestName testName = testFrameworkStrategy.getTestNameFrom(descriptor);

                boolean failedInPreviousRound = previousRoundFailedTests.remove(testName.getClassName(), testName.getName());
                if (failedInPreviousRound && testCompleteEvent.getResultType() == SKIPPED) {
                    currentRoundFailedTests.add(testName.getClassName(), testName.getName());
                }
            }
        }

        delegate.completed(testId, testCompleteEvent);
    }

    @Override
    public void output(Object testId, TestOutputEvent testOutputEvent) {
        delegate.output(testId, testOutputEvent);
    }

    @Override
    public void failure(Object testId, Throwable throwable) {
        final TestDescriptorInternal descriptor = activeDescriptorsById.get(testId);
        if (descriptor != null && descriptor.getClassName() != null) {
            TestName testName = testFrameworkStrategy.getTestNameFrom(descriptor);
            currentRoundFailedTests.add(testName.getClassName(), testName.getName());
        }

        delegate.failure(testId, throwable);
    }

    private boolean lastRun() {
        return currentRoundFailedTests.isEmpty()
            || lastRetry
            || (maxFailures > 0 && currentRoundFailedTests.size() >= maxFailures);
    }

    public RoundResult getResult() {
        return new RoundResult(currentRoundFailedTests, previousRoundFailedTests, lastRun());
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
