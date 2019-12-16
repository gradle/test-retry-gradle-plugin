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
package org.gradle.testretry.internal;

import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.testretry.internal.visitors.SpockParameterClassVisitor;
import org.gradle.testretry.internal.visitors.SpockStepwiseClassVisitor;
import org.gradle.testretry.internal.visitors.TestNGClassVisitor;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RetryTestExecuter implements TestExecuter<JvmTestExecutionSpec> {

    private final Logger logger = LoggerFactory.getLogger(RetryTestExecuter.class);

    private final TestExecuter<JvmTestExecutionSpec> delegate;
    private final Test testTask;
    private final int maxRetries;
    private final int maxFailures;
    private final boolean failOnPassedAfterRetry;
    private final Instantiator instantiator;
    private final ClassLoaderCache classLoaderCache;

    public RetryTestExecuter(
        TestExecuter<JvmTestExecutionSpec> delegate,
        Test test,
        int maxRetries,
        int maxFailures,
        boolean failOnPassedAfterRetry,
        Instantiator instantiator,
        ClassLoaderCache classLoaderCache
    ) {
        this.delegate = delegate;
        this.testTask = test;
        this.maxRetries = maxRetries;
        this.maxFailures = maxFailures;
        this.failOnPassedAfterRetry = failOnPassedAfterRetry;
        this.instantiator = instantiator;
        this.classLoaderCache = classLoaderCache;
    }

    @Override
    public void execute(JvmTestExecutionSpec spec, TestResultProcessor testResultProcessor) {
        if (maxRetries <= 0) {
            delegate.execute(spec, testResultProcessor);
            return;
        }

        RetryTestResultProcessor retryTestResultProcessor = new RetryTestResultProcessor(testResultProcessor, maxFailures);

        // initial run, collecting failures that will need to be retried on subsequent attempts
        delegate.execute(spec, retryTestResultProcessor);
        int totalFailures = retryTestResultProcessor.getRetries().size();

        for (int retryCount = 0; retryCount < maxRetries && (maxFailures <= 0 || totalFailures < maxFailures) && !retryTestResultProcessor.getRetries().isEmpty(); ++retryCount) {
            JvmTestExecutionSpec retryJvmExecutionSpec = createRetryJvmExecutionSpec(spec, testTask, retryTestResultProcessor.getRetries());
            retryTestResultProcessor.nextRetry();
            if (retryCount + 1 == maxRetries) {
                retryTestResultProcessor.lastRetry();
            }
            delegate.execute(retryJvmExecutionSpec, retryTestResultProcessor);
            totalFailures = retryTestResultProcessor.getRetries().size();

            if (!retryTestResultProcessor.getExpectedRetries().isEmpty()) {
                throw new IllegalStateException("org.gradle.test-retry was unable to retry the following test methods, which is unexpected. Please file a bug report at https://github.com/gradle/test-retry-gradle-plugin/issues" +
                    retryTestResultProcessor.getExpectedRetries().stream()
                        .map(retry -> "   " + retry.getClassName() + "#" + retry.getName())
                        .collect(Collectors.joining("\n", "\n", "\n")));
            }
        }

        if (retryTestResultProcessor.getRetries().isEmpty() && !failOnPassedAfterRetry) {
            // all flaky tests have passed at one point.
            // do not fail the task but keep warning of test failures
            testTask.setIgnoreFailures(true);
        }
    }

    private JvmTestExecutionSpec createRetryJvmExecutionSpec(JvmTestExecutionSpec spec, Test testTask, List<TestDescriptorInternal> retries) {
        DefaultTestFilter retriedTestFilter = new DefaultTestFilter();

        TestFramework testFramework = spec.getTestFramework();
        TestFramework retryingTestFramework = testFramework;
        if (testFramework instanceof JUnitTestFramework) {
            retryingTestFramework = new JUnitTestFramework(testTask, retriedTestFilter);
            retriesWithSpockParametersRemoved(spec, retries).stream()
                .filter(retry -> retry.getClassName() != null)
                .forEach(retry -> {
                    if (isSpockStepwiseTest(spec, retry)) {
                        retriedTestFilter.includeTestsMatching(retry.getClassName());
                    } else {
                        String strippedParameterName = retry.getName().replaceAll("\\[\\d+]$", "");
                        retriedTestFilter.includeTest(retry.getClassName(), strippedParameterName);
                        retriedTestFilter.includeTest(retry.getClassName(), retry.getName());
                    }
                });
        } else if (testFramework instanceof JUnitPlatformTestFramework) {
            retryingTestFramework = new JUnitPlatformTestFramework(retriedTestFilter);
            retries.stream()
                .filter(retry -> retry.getClassName() != null)
                .forEach(retry -> {
                    String strippedParameterName = retry.getName().replaceAll("\\([^)]*\\)(\\[\\d+])*$", "");
                    retriedTestFilter.includeTest(retry.getClassName(), strippedParameterName);
                });
        } else if (testFramework instanceof TestNGTestFramework) {
            retryingTestFramework = new TestNGTestFramework(testTask, retriedTestFilter, instantiator, classLoaderCache);
            retriesWithTestNGDependentsAdded(spec, retries).stream()
                .filter(retry -> retry.getClassName() != null)
                .forEach(retry -> {
                    String strippedParameterName = retry.getName().replaceAll("\\[[^)]+](\\(\\d+\\))+$", "");
                    retriedTestFilter.includeTest(retry.getClassName(), strippedParameterName);
                    retriedTestFilter.includeTest(retry.getClassName(), retry.getName());
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
            spec.getPreviousFailedTestClasses()
        );
    }

    private boolean isSpockStepwiseTest(JvmTestExecutionSpec spec, TestDescriptorInternal retry) {
        if (retry.getClassName() == null) {
            return false;
        }

        return spec.getTestClassesDirs().getFiles().stream()
            .map(dir -> new File(dir, retry.getClassName().replace('.', '/') + ".class"))
            .filter(File::exists)
            .findAny()
            .map(testClass -> {
                try (FileInputStream testClassIs = new FileInputStream(testClass)) {
                    ClassReader classReader = new ClassReader(testClassIs);
                    SpockStepwiseClassVisitor visitor = new SpockStepwiseClassVisitor();
                    classReader.accept(visitor, 0);
                    return visitor.isStepwise();
                } catch (Throwable t) {
                    logger.warn("Unable to determine if class " + retry.getClassName() + " is a Spock @Stepwise test", t);
                    return false;
                }
            })
            .orElse(false);
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
                        } catch (Throwable t) {
                            logger.warn("Unable to determine if class " + retry.getClassName() + " has TestNG dependent tests", t);
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
                            SpockParameterClassVisitor visitor = new SpockParameterClassVisitor(retry.getName());
                            classReader.accept(visitor, 0);
                            return new DefaultTestDescriptor("doesnotmatter", retry.getClassName(), visitor.getTestMethodName());
                        } catch (Throwable t) {
                            logger.warn("Unable to determine if class " + retry.getClassName() + " contains Spock @Unroll parameterizations", t);
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
