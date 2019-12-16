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
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestFramework;
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

final class RetryTestFrameworkGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryTestFrameworkGenerator.class);

    private final ClassLoaderCache classLoaderCache;
    private final Instantiator instantiator;

    RetryTestFrameworkGenerator(ClassLoaderCache classLoaderCache, Instantiator instantiator) {
        this.classLoaderCache = classLoaderCache;
        this.instantiator = instantiator;
    }

    TestFramework createRetryingTestFramework(JvmTestExecutionSpec spec, Test testTask, List<TestName> failedTests) {
        DefaultTestFilter retriedTestFilter = new DefaultTestFilter();
        TestFramework testFramework = spec.getTestFramework();

        TestFramework retryingTestFramework;
        if (testFramework instanceof JUnitTestFramework) {
            retryingTestFramework = new JUnitTestFramework(testTask, retriedTestFilter);
            retriesWithSpockParametersRemoved(spec, failedTests).stream()
                .filter(failedTest -> failedTest.getClassName() != null)
                .forEach(failedTest -> {
                    if (isSpockStepwiseTest(spec, failedTest)) {
                        retriedTestFilter.includeTestsMatching(failedTest.getClassName());
                    } else {
                        String strippedParameterName = failedTest.getName().replaceAll("\\[\\d+]$", "");
                        retriedTestFilter.includeTest(failedTest.getClassName(), strippedParameterName);
                        retriedTestFilter.includeTest(failedTest.getClassName(), failedTest.getName());
                    }
                });
        } else if (testFramework instanceof JUnitPlatformTestFramework) {
            retryingTestFramework = new JUnitPlatformTestFramework(retriedTestFilter);
            failedTests.stream()
                .filter(failedTest -> failedTest.getClassName() != null)
                .forEach(failedTest -> {
                    String strippedParameterName = failedTest.getName().replaceAll("\\([^)]*\\)(\\[\\d+])*$", "");
                    retriedTestFilter.includeTest(failedTest.getClassName(), strippedParameterName);
                });
        } else if (testFramework instanceof TestNGTestFramework) {
            retryingTestFramework = new TestNGTestFramework(testTask, retriedTestFilter, instantiator, classLoaderCache);
            retriesWithTestNGDependentsAdded(spec, failedTests).stream()
                .filter(failedTest -> failedTest.getClassName() != null)
                .forEach(failedTest -> {
                    String strippedParameterName = failedTest.getName().replaceAll("\\[[^)]+](\\(\\d+\\))+$", "");
                    retriedTestFilter.includeTest(failedTest.getClassName(), strippedParameterName);
                    retriedTestFilter.includeTest(failedTest.getClassName(), failedTest.getName());
                });
        } else {
            throw new UnsupportedOperationException("Unknown test framework: " + testFramework);
        }

        return retryingTestFramework;
    }

    private static boolean isSpockStepwiseTest(JvmTestExecutionSpec spec, TestName failedTest) {
        if (failedTest.getClassName() == null) {
            return false;
        }

        return spec.getTestClassesDirs().getFiles().stream()
            .map(dir -> new File(dir, failedTest.getClassName().replace('.', '/') + ".class"))
            .filter(File::exists)
            .findAny()
            .map(testClass -> {
                try (FileInputStream testClassIs = new FileInputStream(testClass)) {
                    ClassReader classReader = new ClassReader(testClassIs);
                    SpockStepwiseClassVisitor visitor = new SpockStepwiseClassVisitor();
                    classReader.accept(visitor, 0);
                    return visitor.isStepwise();
                } catch (Throwable t) {
                    LOGGER.warn("Unable to determine if class " + failedTest.getClassName() + " is a Spock @Stepwise test", t);
                    return false;
                }
            })
            .orElse(false);
    }

    private static List<TestName> retriesWithTestNGDependentsAdded(JvmTestExecutionSpec spec, List<TestName> failedTests) {
        return failedTests.stream()
            .filter(failedTest -> failedTest.getClassName() != null)
            .flatMap(failedTest ->
                spec.getTestClassesDirs().getFiles().stream()
                    .map(dir -> new File(dir, failedTest.getClassName().replace('.', '/') + ".class"))
                    .filter(File::exists)
                    .findAny()
                    .map(testClass -> {
                        try (FileInputStream testClassIs = new FileInputStream(testClass)) {
                            ClassReader classReader = new ClassReader(testClassIs);
                            TestNGClassVisitor visitor = new TestNGClassVisitor();
                            classReader.accept(visitor, 0);
                            return visitor.dependsOn(failedTest.getName()).stream()
                                .map(method -> new TestName(failedTest.getClassName(), method));
                        } catch (Throwable t) {
                            LOGGER.warn("Unable to determine if class " + failedTest.getClassName() + " has TestNG dependent tests", t);
                            return Stream.of(failedTest);
                        }
                    })
                    .orElse(Stream.of(failedTest))
            )
            .collect(Collectors.toList());
    }

    private static List<TestName> retriesWithSpockParametersRemoved(JvmTestExecutionSpec spec, List<TestName> failedTests) {
        return failedTests.stream()
            .filter(failedTest -> failedTest.getClassName() != null)
            .map(failedTest ->
                spec.getTestClassesDirs().getFiles().stream()
                    .map(dir -> new File(dir, failedTest.getClassName().replace('.', '/') + ".class"))
                    .filter(File::exists)
                    .findAny()
                    .map(testClass -> {
                        try (FileInputStream testClassIs = new FileInputStream(testClass)) {
                            ClassReader classReader = new ClassReader(testClassIs);
                            SpockParameterClassVisitor visitor = new SpockParameterClassVisitor(failedTest.getName());
                            classReader.accept(visitor, 0);
                            return new TestName(failedTest.getClassName(), visitor.getTestMethodName());
                        } catch (Throwable t) {
                            LOGGER.warn("Unable to determine if class " + failedTest.getClassName() + " contains Spock @Unroll parameterizations", t);
                            return failedTest;
                        }
                    })
                    .orElse(failedTest)
            )
            .collect(Collectors.toList());
    }
}
