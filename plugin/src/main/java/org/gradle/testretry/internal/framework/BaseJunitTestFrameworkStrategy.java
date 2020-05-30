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
package org.gradle.testretry.internal.framework;

import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.testretry.internal.TestName;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class BaseJunitTestFrameworkStrategy implements TestFrameworkStrategy {

    public static final Logger LOGGER = LoggerFactory.getLogger(JunitTestFrameworkStrategy.class);
    static final Set<String> ERROR_SYNTHETIC_TEST_NAMES = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            "classMethod",
            "executionError",
            "initializationError"
        ))
    );

    @Override
    public void removeSyntheticFailures(Set<TestName> nonExecutedFailedTests, TestDescriptorInternal descriptor) {
        ERROR_SYNTHETIC_TEST_NAMES.forEach(testName -> nonExecutedFailedTests.remove(new TestName(descriptor.getClassName(), testName)));
    }

    protected DefaultTestFilter createRetryFilter(JvmTestExecutionSpec spec, Set<TestName> failedTests) {
        DefaultTestFilter retriedTestFilter = new DefaultTestFilter();
        BaseJunitTestFrameworkStrategy.retriesWithSpockParametersRemoved(spec, failedTests).stream()
            .filter(failedTest -> failedTest.getClassName() != null)
            .forEach(failedTest -> {
                if (ERROR_SYNTHETIC_TEST_NAMES.contains(failedTest.getName())) {
                    retriedTestFilter.includeTestsMatching(failedTest.getClassName());
                } else if (BaseJunitTestFrameworkStrategy.isSpockStepwiseTest(spec, failedTest)) {
                    retriedTestFilter.includeTestsMatching(failedTest.getClassName());
                } else {
                    String strippedParameterName = failedTest.getName().replaceAll("(?:\\([^)]*?\\)|\\[[^]]*?])*$", "");
                    retriedTestFilter.includeTest(failedTest.getClassName(), strippedParameterName);
                    retriedTestFilter.includeTest(failedTest.getClassName(), failedTest.getName());
                }
            });
        return retriedTestFilter;
    }

    public static List<TestName> retriesWithSpockParametersRemoved(JvmTestExecutionSpec spec, Set<TestName> failedTests) {
        List<TestName> originalFailedTests = new ArrayList<>(failedTests);

        List<TestName> retries = failedTests.stream()
            .flatMap(failedTest ->
                spec.getTestClassesDirs().getFiles().stream()
                    .map(dir -> new File(dir, failedTest.getClassName().replace('.', '/') + ".class"))
                    .filter(File::exists)
                    .flatMap(testClass -> {
                        try (FileInputStream testClassIs = new FileInputStream(testClass)) {
                            ClassReader classReader = new ClassReader(testClassIs);
                            SpockParameterClassVisitor visitor = new SpockParameterClassVisitor(failedTest.getName(), spec);
                            classReader.accept(visitor, 0);
                            return visitor.getTestMethodNames().stream().map(name -> new TestName(failedTest.getClassName(), name));
                        } catch (Throwable t) {
                            LOGGER.warn("Unable to determine if class " + failedTest.getClassName() + " contains Spock @Unroll parameterizations", t);
                            return Stream.of(failedTest);
                        }
                    })
            )
            .collect(Collectors.toList());

        return retries.isEmpty() ? originalFailedTests : retries;
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
}
