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

public class RetryTestResultProcessor implements TestResultProcessor {

    private TestResultProcessor delegate;

    private boolean lastRetry = false;

    private Map<Object, TestDescriptorInternal> all = new HashMap<Object, TestDescriptorInternal>();
    private List<TestDescriptorInternal> retries = new ArrayList<>();

    public RetryTestResultProcessor(TestResultProcessor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptorInternal, TestStartEvent testStartEvent) {
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
//        System.out.println("RetryTestResultProcessor.completed");
//        System.out.println("o = " + o + ", testCompleteEvent = " + testCompleteEvent.getResultType());
        TestDescriptorInternal testDescriptorInternal = all.get(o);
        if (testDescriptorInternal != null && retries.contains(testDescriptorInternal)) {
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

    public void lastRetry() {
        lastRetry = true;
    }

    public void reset() {
        retries.clear();
        all.clear();
    }

    public List<TestDescriptorInternal> getRetries() {
        return retries;
    }
}
