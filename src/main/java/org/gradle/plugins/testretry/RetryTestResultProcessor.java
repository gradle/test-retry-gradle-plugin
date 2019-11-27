package org.gradle.plugins.testretry;

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;

public class RetryTestResultProcessor implements TestResultProcessor {

    private TestResultProcessor delegate;

    private boolean lastRetry = true;

    public RetryTestResultProcessor(TestResultProcessor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptorInternal, TestStartEvent testStartEvent) {
        delegate.started(testDescriptorInternal, testStartEvent);
    }

    @Override
    public void completed(Object o, TestCompleteEvent testCompleteEvent) {
        delegate.completed(o, testCompleteEvent);
    }

    @Override
    public void output(Object o, TestOutputEvent testOutputEvent) {
        delegate.output(o, testOutputEvent);
    }

    @Override
    public void failure(Object o, Throwable throwable) {
        if(lastRetry) {
            delegate.failure(o, throwable);
        } else {
            // nothing for now
        }
    }

    public void lastRetry() {
        lastRetry = true;
    }
}
