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

import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.tasks.testing.*;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.reflect.Instantiator;

import java.util.List;
import java.util.stream.Stream;

public class RetryTestExecuter implements TestExecuter<JvmTestExecutionSpec> {

    private final TestExecuter<JvmTestExecutionSpec> delegate;
    private final Test testTask;
    private final RetryTestListener retryTestListener;
    private final int maxRetries;
    private final Instantiator instantiator;
    private final ClassLoaderCache classLoaderCache;

    public RetryTestExecuter(TestExecuter<JvmTestExecutionSpec> delegate,
                             Test test,
                             RetryTestListener retryTestListener,
                             int maxRetries, Instantiator instantiator, ClassLoaderCache classLoaderCache) {
        this.delegate = delegate;
        this.testTask = test;
        this.retryTestListener = retryTestListener;
        this.maxRetries = maxRetries;
        this.instantiator = instantiator;
        this.classLoaderCache = classLoaderCache;
    }

    @Override
    public void execute(JvmTestExecutionSpec spec, TestResultProcessor testResultProcessor) {
        RetryTestResultProcessor retryTestResultProcessor = new RetryTestResultProcessor(testResultProcessor);
        if (maxRetries > 0) {
            delegate.execute(spec, retryTestResultProcessor);
            for(int retryCount = 0; retryCount < maxRetries && !retryTestResultProcessor.getRetries().isEmpty(); retryCount++) {
                retryTestListener.reset();
                JvmTestExecutionSpec retryJvmExecutionSpec = createRetryJvmExecutionSpec(spec, testTask, retryTestResultProcessor.getRetries());
                retryTestResultProcessor.reset();
                if (retryCount + 1 == maxRetries) {
                    retryTestResultProcessor.lastRetry();
                }
                delegate.execute(retryJvmExecutionSpec, retryTestResultProcessor);
            }
        } else {
            delegate.execute(spec, testResultProcessor);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private JvmTestExecutionSpec createRetryJvmExecutionSpec(JvmTestExecutionSpec spec, Test testTask, List<TestDescriptorInternal> retries) {
        DefaultTestFilter retriedTestFilter = new DefaultTestFilter();
        Stream<TestDescriptorInternal> testDescriptors = retries.stream()
                .filter(d -> d.getClassName() != null);

        TestFramework testFramework = spec.getTestFramework();
        TestFramework retryingTestFramework = testFramework;
        if(testFramework instanceof JUnitTestFramework) {
            retryingTestFramework = new JUnitTestFramework(testTask, retriedTestFilter);
            testDescriptors.forEach(d -> {
                String strippedParameterName = d.getName().replaceAll("\\[\\d+]", "");
                retriedTestFilter.includeTest(d.getClassName(), strippedParameterName);
                retriedTestFilter.includeTest(d.getClassName(), d.getName());
            });
        }
        else if(testFramework instanceof JUnitPlatformTestFramework) {
            retryingTestFramework = new JUnitPlatformTestFramework(retriedTestFilter);
            testDescriptors.forEach(d -> {
                String strippedParameterName = d.getName().replaceAll("\\([^)]+](\\[\\d+])+", "");
                retriedTestFilter.includeTest(d.getClassName(), strippedParameterName);
                retriedTestFilter.includeTest(d.getClassName(), d.getName());
            });
        }
        else if(testFramework instanceof TestNGTestFramework) {
            retryingTestFramework = new TestNGTestFramework(testTask, retriedTestFilter, instantiator, classLoaderCache);
            testDescriptors.forEach(d -> {
                String strippedParameterName = d.getName().replaceAll("\\[[^)]+](\\(\\d+\\))+", "");
                retriedTestFilter.includeTest(d.getClassName(), strippedParameterName);
                retriedTestFilter.includeTest(d.getClassName(), d.getName());
            });
        }

        return new JvmTestExecutionSpec(retryingTestFramework,
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
