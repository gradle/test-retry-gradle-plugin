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
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED;

final class RetryTestResultProcessor implements TestResultProcessor {

    private final TestFrameworkStrategy testFrameworkStrategy;
    private final TestResultProcessor delegate;

    private final int maxFailures;
    private boolean lastRetry;

    private final Map<Object, TestDescriptorInternal> activeDescriptorsById = new HashMap<>();
    private final Set<TestName> failedTestsInCurrentRound = new HashSet<>();
    private final Set<TestName> failedTestsFromPreviousRoundNotYetExecutedInCurrentRound = new HashSet<>();

    private Object rootTestDescriptorId;

    RetryTestResultProcessor(TestFrameworkStrategy testFrameworkStrategy, TestResultProcessor delegate, int maxFailures) {
        this.testFrameworkStrategy = testFrameworkStrategy;
        this.delegate = delegate;
        this.maxFailures = maxFailures;
    }

    @Override
    public void started(TestDescriptorInternal descriptor, TestStartEvent testStartEvent) {
        testFrameworkStrategy.removeSyntheticFailures(failedTestsFromPreviousRoundNotYetExecutedInCurrentRound, descriptor);

        if (rootTestDescriptorId == null) {
            rootTestDescriptorId = descriptor.getId();
            activeDescriptorsById.put(descriptor.getId(), descriptor);
            delegate.started(descriptor, testStartEvent);
        } else if (!descriptor.getId().equals(rootTestDescriptorId)) {
            if (!descriptor.isComposite()) {
                activeDescriptorsById.put(descriptor.getId(), descriptor);
            }
            delegate.started(descriptor, testStartEvent);
        }
    }

    @Override
    public void completed(Object testId, TestCompleteEvent testCompleteEvent) {
        TestDescriptorInternal descriptor = activeDescriptorsById.remove(testId);
        if (descriptor != null && descriptor.getClassName() != null) {
            TestName test = testFrameworkStrategy.getTestNameFrom(descriptor);
            boolean failedInPreviousRound = failedTestsFromPreviousRoundNotYetExecutedInCurrentRound.remove(test);
            if (failedInPreviousRound && testCompleteEvent.getResultType() == SKIPPED) {
                failedTestsInCurrentRound.add(test);
            }
        }

        if (!testId.equals(rootTestDescriptorId) || lastRun()) {
            delegate.completed(testId, testCompleteEvent);
        }
    }

    @Override
    public void output(Object testId, TestOutputEvent testOutputEvent) {
        delegate.output(testId, testOutputEvent);
    }

    @Override
    public void failure(Object testId, Throwable throwable) {
        final TestDescriptorInternal descriptor = activeDescriptorsById.get(testId);
        if (descriptor != null && descriptor.getClassName() != null) {
            failedTestsInCurrentRound.add(testFrameworkStrategy.getTestNameFrom(descriptor));
        }
        delegate.failure(testId, throwable);
    }

    private boolean lastRun() {
        return failedTestsInCurrentRound.isEmpty() || lastRetry || (maxFailures > 0 && failedTestsInCurrentRound.size() >= maxFailures);
    }

    public RoundResult getResult() {
        return new RoundResult(
            copy(failedTestsInCurrentRound),
            copy(failedTestsFromPreviousRoundNotYetExecutedInCurrentRound),
            lastRun()
        );
    }

    @NotNull
    private Set<TestName> copy(Set<TestName> nonExecutedFailedTests) {
        return nonExecutedFailedTests.isEmpty() ? Collections.emptySet() : new HashSet<>(nonExecutedFailedTests);
    }

    public void reset(boolean lastRetry) {
        if (lastRun()) {
            throw new IllegalStateException("processor has completed");
        }
        failedTestsFromPreviousRoundNotYetExecutedInCurrentRound.clear();
        failedTestsFromPreviousRoundNotYetExecutedInCurrentRound.addAll(failedTestsInCurrentRound);
        failedTestsInCurrentRound.clear();
        activeDescriptorsById.clear();
        this.lastRetry = lastRetry;
    }

    static final class RoundResult {

        final Set<TestName> failedTests;
        final Set<TestName> nonRetriedTests;
        final boolean lastRound;

        public RoundResult(Set<TestName> failedTests, Set<TestName> nonRetriedTests, boolean lastRound) {
            this.failedTests = failedTests;
            this.nonRetriedTests = nonRetriedTests;
            this.lastRound = lastRound;
        }
    }
}
