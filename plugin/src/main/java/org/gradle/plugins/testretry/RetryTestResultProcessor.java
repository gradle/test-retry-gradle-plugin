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
package org.gradle.plugins.testretry;

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.stream.Collectors.toCollection;

public class RetryTestResultProcessor implements TestResultProcessor {

    private final TestResultProcessor delegate;
    private final int maxFailures;

    private boolean retry;
    private boolean lastRetry = false;

    private int totalFailures = 0;

    private Map<Object, TestDescriptorInternal> all = new ConcurrentHashMap<>();
    private List<TestDescriptorInternal> retries = new CopyOnWriteArrayList<>();
    private List<TestDescriptorNameOnly> expectedRetries = Collections.emptyList();
    private Object rootTestDescriptorId;
    private boolean rootFired;

    public RetryTestResultProcessor(TestResultProcessor delegate, int maxFailures) {
        this.delegate = delegate;
        this.maxFailures = maxFailures;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptorInternal, TestStartEvent testStartEvent) {
        expectedRetries.remove(TestDescriptorNameOnly.fromTestDescriptorInternal(testDescriptorInternal));

        if (!rootFired) {
            rootFired = true;
            if (retry) {
                return;
            } else {
                rootTestDescriptorId = testDescriptorInternal.getId();
                all.put(testDescriptorInternal.getId(), testDescriptorInternal);
            }
        }
        if (!testDescriptorInternal.isComposite()) {
            all.put(testDescriptorInternal.getId(), testDescriptorInternal);
        }

        delegate.started(testDescriptorInternal, testStartEvent);
    }

    @Override
    public void completed(Object o, TestCompleteEvent testCompleteEvent) {
        if (!lastRun() && o.equals(rootTestDescriptorId)) {
            return;
        }
        TestDescriptorInternal testDescriptor = all.get(o);
        if (testDescriptor != null && retries.contains(testDescriptor)) {
            delegate.completed(o, new TestCompleteEvent(testCompleteEvent.getEndTime(), TestResult.ResultType.FAILURE));
        } else {
            delegate.completed(o, testCompleteEvent);
        }
    }

    @Override
    public void output(Object o, TestOutputEvent testOutputEvent) {
        delegate.output(o, testOutputEvent);
    }

    @Override
    public void failure(Object o, Throwable throwable) {
        TestDescriptorInternal testDescriptorInternal = all.get(o);
        if (testDescriptorInternal != null) {
            retries.add(testDescriptorInternal);
            totalFailures++;
        }
        delegate.failure(o, throwable);
    }

    private boolean lastRun() {
        return retries.isEmpty() || lastRetry || totalFailures > maxFailures;
    }

    public void lastRetry() {
        lastRetry = true;
    }

    public void nextRetry() {
        expectedRetries = retries.stream().map(TestDescriptorNameOnly::fromTestDescriptorInternal)
                .collect(toCollection(ArrayList::new));
        totalFailures = 0;
        retries.clear();
        all.clear();
        retry = true;
        rootFired = false;
    }

    public List<TestDescriptorInternal> getRetries() {
        return retries;
    }

    public List<TestDescriptorNameOnly> getExpectedRetries() {
        return expectedRetries;
    }

    public static class TestDescriptorNameOnly {
        private final String className;

        @Nonnull
        private final String name;

        private TestDescriptorNameOnly(String className, @Nonnull String name) {
            this.className = className;
            this.name = name;
        }

        static TestDescriptorNameOnly fromTestDescriptorInternal(TestDescriptorInternal testDescriptorInternal) {
            return new TestDescriptorNameOnly(testDescriptorInternal.getClassName(), testDescriptorInternal.getName());
        }

        public String getClassName() {
            return className;
        }

        @Nonnull
        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestDescriptorNameOnly that = (TestDescriptorNameOnly) o;
            return Objects.equals(className, that.className) &&
                    name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, name);
        }
    }
}
