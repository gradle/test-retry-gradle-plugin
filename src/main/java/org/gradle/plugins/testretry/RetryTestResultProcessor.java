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

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class RetryTestResultProcessor implements TestResultProcessor {

    private TestResultProcessor delegate;

    private boolean lastRetry = false;

    private Map<Object, TestDescriptorInternal> all = new ConcurrentHashMap<>();
    private List<TestDescriptorInternal> retries = new CopyOnWriteArrayList<>();
    private boolean retry;

    public RetryTestResultProcessor(TestResultProcessor delegate) {
        this.delegate = delegate;
    }


    @Override
    public void started(TestDescriptorInternal testDescriptorInternal, TestStartEvent testStartEvent) {

        if(testDescriptorInternal.isRoot() ) {
            if(retry) {
                return;
            } else {
                all.put(testDescriptorInternal.getId(), testDescriptorInternal);
            }
        }
        if (!testDescriptorInternal.isComposite()) {
            all.put(testDescriptorInternal.getId(), testDescriptorInternal);
        }

//        System.out.println("RetryTestResultProcessor.started");
//        System.out.println("testDescriptorInternal = " + testDescriptorInternal.getDisplayName() + "  --  " + testDescriptorInternal.getId() + ", testStartEvent = " + testStartEvent);
        delegate.started(testDescriptorInternal, testStartEvent);
//        System.out.println("RetryTestResultProcessor.started finished");
    }

    @Override
    public void completed(Object o, TestCompleteEvent testCompleteEvent) {

        TestDescriptorInternal testDescriptor = all.get(o);
        if(!(retries.isEmpty() && lastRetry) && testDescriptor != null && testDescriptor.isRoot() ) {
            return;
        }
//        System.out.println("RetryTestResultProcessor.completed");
//        System.out.println("o = " + o + ", testCompleteEvent = " + testCompleteEvent.getResultType());
        if (testDescriptor != null && retries.contains(testDescriptor)) {
            delegate.completed(o, new TestCompleteEvent(testCompleteEvent.getEndTime(), TestResult.ResultType.FAILURE));
        } else {
            delegate.completed(o, testCompleteEvent);
        }
//        System.out.println("RetryTestResultProcessor.completed finished");

    }

    @Override
    public void output(Object o, TestOutputEvent testOutputEvent) {
//        System.out.println("RetryTestResultProcessor.output");
//        System.out.println("o = " + o + ", testOutputEvent = " + testOutputEvent);
        delegate.output(o, testOutputEvent);
//        System.out.println("RetryTestResultProcessor.output finished");
    }

    @Override
    public void failure(Object o, Throwable throwable) {
//        System.out.println("RetryTestResultProcessor.failure");
//        System.out.println("o = " + o + ", throwable = " + throwable);
        if (lastRetry) {
            delegate.failure(o, throwable);
        } else {
            TestDescriptorInternal testDescriptorInternal = all.get(o);
            if (testDescriptorInternal != null) {
                retries.add(testDescriptorInternal);
            }
        }
//        System.out.println("RetryTestResultProcessor.failure finished");

    }

    class RetryTestDescriptor {

    }

    public void lastRetry() {
        lastRetry = true;
    }

    public void setupRetry() {
        retries.clear();
        all.clear();
        retry = true;
    }

    public List<TestDescriptorInternal> getRetries() {
        return retries;
    }
}
