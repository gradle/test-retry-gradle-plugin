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
import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RetryTestExecuter implements TestExecuter<JvmTestExecutionSpec> {

    private final TestExecuter<JvmTestExecutionSpec> delegate;
    private final Test testTask;
    private final int maxRetries;
    private final Instantiator instantiator;
    private final ClassLoaderCache classLoaderCache;

    public RetryTestExecuter(TestExecuter<JvmTestExecutionSpec> delegate,
                             Test test,
                             int maxRetries,
                             Instantiator instantiator,
                             ClassLoaderCache classLoaderCache) {
        this.delegate = delegate;
        this.testTask = test;
        this.maxRetries = maxRetries;
        this.instantiator = instantiator;
        this.classLoaderCache = classLoaderCache;
    }

    @Override
    public void execute(JvmTestExecutionSpec spec, TestResultProcessor testResultProcessor) {
        RetryTestResultProcessor retryTestResultProcessor = new RetryTestResultProcessor(testResultProcessor);
        if (maxRetries > 0) {
            delegate.execute(spec, retryTestResultProcessor);
            for (int retryCount = 0; retryCount < maxRetries && !retryTestResultProcessor.getRetries().isEmpty(); retryCount++) {
                JvmTestExecutionSpec retryJvmExecutionSpec = createRetryJvmExecutionSpec(spec, testTask, retryTestResultProcessor.getRetries());
                retryTestResultProcessor.reset();
                if (retryCount + 1 == maxRetries) {
                    retryTestResultProcessor.lastRetry();
                }
                delegate.execute(retryJvmExecutionSpec, retryTestResultProcessor);
            }
            if(retryTestResultProcessor.getRetries().isEmpty()) {
                // all flaky tests have passed at one point.
                // Do not fail the task but keep warning of test failures
                testTask.setIgnoreFailures(true);
            }
        } else {
            delegate.execute(spec, testResultProcessor);
        }
    }

    private JvmTestExecutionSpec createRetryJvmExecutionSpec(JvmTestExecutionSpec spec, Test testTask, List<TestDescriptorInternal> retries) {
        DefaultTestFilter retriedTestFilter = new DefaultTestFilter();

        TestFramework testFramework = spec.getTestFramework();
        TestFramework retryingTestFramework = testFramework;
        if (testFramework instanceof JUnitTestFramework) {
            retryingTestFramework = new JUnitTestFramework(testTask, retriedTestFilter);
            retriesWithSpockParametersRemoved(spec, retries).stream()
                    .filter(d -> d.getClassName() != null)
                    .forEach(d -> {
                        String strippedParameterName = d.getName().replaceAll("\\[\\d+]", "");
                        retriedTestFilter.includeTest(d.getClassName(), strippedParameterName);
                        retriedTestFilter.includeTest(d.getClassName(), d.getName());
                    });
        } else if (testFramework instanceof JUnitPlatformTestFramework) {
            retryingTestFramework = new JUnitPlatformTestFramework(retriedTestFilter);
            retries.stream()
                    .filter(d -> d.getClassName() != null)
                    .forEach(d -> {
                        String strippedParameterName = d.getName().replaceAll("\\([^)]*\\)(\\[\\d+])*", "");
                        retriedTestFilter.includeTest(d.getClassName(), strippedParameterName);
                    });
        } else if (testFramework instanceof TestNGTestFramework) {
            retryingTestFramework = new TestNGTestFramework(testTask, retriedTestFilter, instantiator, classLoaderCache);
            retriesWithTestNGDependentsAdded(spec, retries).stream()
                    .filter(d -> d.getClassName() != null)
                    .forEach(d -> {
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

    private List<TestDescriptorInternal> retriesWithTestNGDependentsAdded(JvmTestExecutionSpec spec, List<TestDescriptorInternal> retries) {
        return retries.stream()
                .filter(retry -> retry.getClassName() != null)
                .flatMap(retry ->
                        spec.getTestClassesDirs().getFiles().stream()
                                .map(dir -> new File(dir, retry.getClassName().replace('.', '/') + ".class"))
                                .filter(File::exists)
                                .findAny()
                                .map(testClass -> {
                                    try (FileInputStream testClassIs = new FileInputStream(testClass)) {
                                        ClassReader classReader = new ClassReader(testClassIs);
                                        TestNGClassVisitor visitor = new TestNGClassVisitor();
                                        classReader.accept(visitor, 0);
                                        return visitor.dependsOn(retry.getName()).stream()
                                                .map(method -> new DefaultTestDescriptor("doesnotmatter", retry.getClassName(), method));
                                    } catch(Throwable t) {
                                        t.printStackTrace();
                                        return Stream.of(retry);
                                    }
                                })
                                .orElse(Stream.of(retry))
                ).collect(Collectors.toList());
    }

    private List<TestDescriptorInternal> retriesWithSpockParametersRemoved(JvmTestExecutionSpec spec, List<TestDescriptorInternal> retries) {
        return retries.stream()
                .filter(retry -> retry.getClassName() != null)
                .map(retry ->
                        spec.getTestClassesDirs().getFiles().stream()
                                .map(dir -> new File(dir, retry.getClassName().replace('.', '/') + ".class"))
                                .filter(File::exists)
                                .findAny()
                                .map(testClass -> {
                                    try (FileInputStream testClassIs = new FileInputStream(testClass)) {
                                        ClassReader classReader = new ClassReader(testClassIs);
                                        SpockClassVisitor visitor = new SpockClassVisitor(retry.getName());
                                        classReader.accept(visitor, 0);
                                        return new DefaultTestDescriptor("doesnotmatter", retry.getClassName(), visitor.getTestMethodName());
                                    } catch(Throwable t) {
                                        t.printStackTrace();
                                        return retry;
                                    }
                                })
                                .orElse(retry)
                ).collect(Collectors.toList());
    }

    @Override
    public void stopNow() {
        delegate.stopNow();
    }
}
