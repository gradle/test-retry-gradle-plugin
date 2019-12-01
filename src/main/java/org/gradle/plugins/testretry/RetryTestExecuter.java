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

import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.tasks.testing.Test;

import java.util.List;

public class RetryTestExecuter implements TestExecuter<JvmTestExecutionSpec> {

    private final TestExecuter<JvmTestExecutionSpec> delegate;
    private Test testTask;
    private RetryTestListener retryTestListener;
    private final RetryTestTaskExtension extension;

    public RetryTestExecuter(TestExecuter<JvmTestExecutionSpec> delegate, Test test, RetryTestListener retryTestListener, RetryTestTaskExtension extension) {
        this.delegate = delegate;
        this.testTask = test;
        this.retryTestListener = retryTestListener;
        this.extension = extension;
    }

    @Override
    public void execute(JvmTestExecutionSpec spec, TestResultProcessor testResultProcessor) {
        RetryTestResultProcessor retryTestResultProcessor = new RetryTestResultProcessor(testResultProcessor);
        if (extension.getMaxRetries() > 0) {
            delegate.execute(spec, retryTestResultProcessor);
            int retryCount = 0;
//            System.out.println("retries = " + extension.getMaxRetries());
            while (retryCount < extension.getMaxRetries() && retryTestResultProcessor.getRetries().size() > 0) {
                retryTestListener.reset();
                JvmTestExecutionSpec retryJvmExecutionSpec = createRetryJvmExecutionSpec(spec, testTask, retryTestResultProcessor.getRetries());
                retryTestResultProcessor.setupRetry();
//                System.out.println("\n\n\n");
//                System.out.println("retryCount = " + retryCount);

                if (retryCount + 1 == extension.getMaxRetries()) {
                    retryTestResultProcessor.lastRetry();
                }
                delegate.execute(retryJvmExecutionSpec, retryTestResultProcessor);
                retryCount++;
            }
        } else {
//            System.out.println("no retry");
            delegate.execute(spec, testResultProcessor);
        }
    }

    private JvmTestExecutionSpec createRetryJvmExecutionSpec(JvmTestExecutionSpec spec, Test testTask, List<TestDescriptorInternal> retries) {
//        JUnitTestFramework originTestFramework = (JUnitTestFramework) spec.getTestFramework();
        // TODO fix testng support
        DefaultTestFilter retriedTestFilter = new DefaultTestFilter();
        retries.forEach((d) -> retriedTestFilter.includeTest(d.getClassName(), d.getName()));
        JUnitTestFramework jUnitTestFramework = new JUnitTestFramework(testTask, retriedTestFilter);
        return new JvmTestExecutionSpec(jUnitTestFramework,
            spec.getClasspath(),
            spec.getCandidateClassFiles(),
            spec.isScanForTestClasses(),
            spec.getTestClassesDirs(),
            spec.getPath(),
            spec.getIdentityPath(),
            spec.getForkEvery(),
            spec.getJavaForkOptions(),
            spec.getMaxParallelForks(),
            spec.getPreviousFailedTestClasses());
    }

    @Override
    public void stopNow() {
        delegate.stopNow();
    }
}
