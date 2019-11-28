package org.gradle.plugins.testretry;

import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.tasks.testing.Test;

public class RetryTestExecuter implements TestExecuter<JvmTestExecutionSpec> {

    private final TestExecuter<JvmTestExecutionSpec> delegate;
    private RetryTestListener retryTestListener;
    private final RetryTestTaskExtension extension;

    public RetryTestExecuter(TestExecuter<JvmTestExecutionSpec> delegate, RetryTestListener retryTestListener, RetryTestTaskExtension extension) {
        this.delegate = delegate;
        this.retryTestListener = retryTestListener;
        this.extension = extension;
    }

    @Override
    public void execute(JvmTestExecutionSpec spec, TestResultProcessor testResultProcessor) {
        RetryTestResultProcessor retryTestResultProcessor = new RetryTestResultProcessor(testResultProcessor);
        if(extension.getMaxRetries() > 0) {
            delegate.execute(spec, retryTestResultProcessor);
            int retryCount = 0;
//            System.out.println("retries = " + extension.getMaxRetries());
            while(retryCount < extension.getMaxRetries() && retryTestResultProcessor.getRetries().size() > 0 ) {
                retryTestListener.reset();
                if(retryCount + 1 == extension.getMaxRetries()) {
                    retryTestResultProcessor.lastRetry();
                }
                delegate.execute(spec, retryTestResultProcessor);
                retryCount++;
            }
        } else {
//            System.out.println("no retry");
            delegate.execute(spec, testResultProcessor);
        }
    }

    @Override
    public void stopNow() {
        delegate.stopNow();
    }
}
