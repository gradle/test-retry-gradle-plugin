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
package org.gradle.testretry.internal.executer.framework;

import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.testretry.internal.executer.TestName;
import org.gradle.testretry.internal.executer.TestsReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
    public boolean isSyntheticFailure(String testName) {
        return ERROR_SYNTHETIC_TEST_NAMES.contains(testName);
    }

    protected DefaultTestFilter createRetryFilter(TestsReader testsReader, Set<TestName> failedTests, boolean canRunParameterizedSpockMethods) {
        DefaultTestFilter retriedTestFilter = new DefaultTestFilter();
        transformSpockTests(testsReader, failedTests, canRunParameterizedSpockMethods)
            .forEach(failedTest -> {
                if (ERROR_SYNTHETIC_TEST_NAMES.contains(failedTest.getName())) {
                    retriedTestFilter.includeTestsMatching(failedTest.getClassName());
                } else if (isSpockStepwiseTest(testsReader, failedTest)) {
                    retriedTestFilter.includeTestsMatching(failedTest.getClassName());
                } else if (failedTest.getName() != null) {
                    String strippedParameterName = failedTest.getName().replaceAll("(?:\\([^)]*?\\)|\\[[^]]*?])*$", "");
                    retriedTestFilter.includeTest(failedTest.getClassName(), strippedParameterName);
                    retriedTestFilter.includeTest(failedTest.getClassName(), failedTest.getName());
                } else {
                    retriedTestFilter.includeTest(failedTest.getClassName(), null);
                }
            });
        return retriedTestFilter;
    }

    private static Stream<TestName> transformSpockTests(TestsReader testsReader, Set<TestName> failedTests, boolean canRunParameterizedMethods) {
        return failedTests.stream()
            .flatMap(failedTest -> transformSpockTests(testsReader, failedTest, canRunParameterizedMethods));
    }

    private static Stream<TestName> transformSpockTests(TestsReader testsReader, TestName failedTest, boolean canRunParameterizedMethods) {
        try {
            Optional<SpockParameterClassVisitor.Result> resultOpt = testsReader.readTestClassDirClass(failedTest.getClassName(), () -> new SpockParameterClassVisitor(failedTest.getName(), testsReader));
            if (resultOpt.isPresent()) {
                SpockParameterClassVisitor.Result result = resultOpt.get();
                Set<String> matchedMethodNames = result.getMatchedMethodNames();
                if (matchedMethodNames.isEmpty()) {
                    return Stream.of(failedTest);
                }

                if (!result.isFoundLiteralMethodName() && !canRunParameterizedMethods) {
                    return Stream.of(new TestName(failedTest.getClassName(), null));
                } else {
                    return matchedMethodNames.stream().map(name -> new TestName(failedTest.getClassName(), name));
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Unable to determine if class " + failedTest.getClassName() + " contains Spock @Unroll parameterizations", t);
        }

        return Stream.of(failedTest);
    }

    @NotNull
    private static Optional<File> classFile(JvmTestExecutionSpec spec, TestName failedTest) {
        return spec.getTestClassesDirs().getFiles().stream()
            .map(dir -> new File(dir, failedTest.getClassName().replace('.', '/') + ".class"))
            .filter(File::exists)
            .findFirst();
    }

    private static boolean isSpockStepwiseTest(TestsReader testsReader, TestName failedTest) {
        if (failedTest.getClassName() == null) {
            return false;
        }

        try {
            return testsReader.readTestClassDirClass(failedTest.getClassName(), SpockStepwiseClassVisitor::new)
                .orElse(false);
        } catch (Throwable t) {
            LOGGER.warn("Unable to determine if class " + failedTest.getClassName() + " is a Spock @Stepwise test", t);
            return false;
        }
    }

    @Override
    public TestName getTestNameFrom(TestDescriptor descriptor) {
        return new TestName(descriptor.getClassName(), descriptor.getName());
    }

}
