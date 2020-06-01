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
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
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
    public void removeSyntheticFailures(Set<TestName> nonExecutedFailedTests, TestDescriptorInternal descriptor) {
        ERROR_SYNTHETIC_TEST_NAMES.forEach(testName -> nonExecutedFailedTests.remove(new TestName(descriptor.getClassName(), testName)));
    }

    protected DefaultTestFilter createRetryFilter(JvmTestExecutionSpec spec, Set<TestName> failedTests, boolean canRunParameterizedSpockMethods) {
        DefaultTestFilter retriedTestFilter = new DefaultTestFilter();
        transformSpockTests(spec, failedTests, canRunParameterizedSpockMethods)
            .filter(failedTest -> failedTest.getClassName() != null)
            .forEach(failedTest -> {
                if (ERROR_SYNTHETIC_TEST_NAMES.contains(failedTest.getName())) {
                    retriedTestFilter.includeTestsMatching(failedTest.getClassName());
                } else if (isSpockStepwiseTest(spec, failedTest)) {
                    retriedTestFilter.includeTestsMatching(failedTest.getClassName());
                } else if (failedTest.getName() != null) {
                    String strippedParameterName = failedTest.getName().replaceAll("(?:\\([^)]*?\\)|\\[[^]]*?])*$", "");
                    retriedTestFilter.includeTest(failedTest.getClassName(), strippedParameterName);
                    retriedTestFilter.includeTest(failedTest.getClassName(), failedTest.getName());
                } else {
                    retriedTestFilter.includeTestsMatching(failedTest.getClassName());
                }
            });
        return retriedTestFilter;
    }

    private static Stream<TestName> transformSpockTests(JvmTestExecutionSpec spec, Set<TestName> failedTests, boolean canRunParameterizedMethods) {
        return failedTests.stream()
            .flatMap(failedTest -> transformSpockTests(spec, failedTest, canRunParameterizedMethods));
    }

    private static Stream<TestName> transformSpockTests(JvmTestExecutionSpec spec, TestName failedTest, boolean canRunParameterizedMethods) {
        Optional<File> classFileOptional = classFile(spec, failedTest);
        if (classFileOptional.isPresent()) {
            try (FileInputStream testClassIs = new FileInputStream(classFileOptional.get())) {
                ClassReader classReader = new ClassReader(testClassIs);
                SpockParameterClassVisitor visitor = new SpockParameterClassVisitor(failedTest.getName(), spec);
                classReader.accept(visitor, 0);

                Set<String> matchedMethodNames = visitor.getMatchedMethodNames();
                if (matchedMethodNames.isEmpty()) {
                    return Stream.of(failedTest);
                }

                if (!visitor.isFoundLiteralMethodName() && !canRunParameterizedMethods) {
                    return Stream.of(new TestName(failedTest.getClassName(), null));
                } else {
                    return matchedMethodNames.stream().map(name -> new TestName(failedTest.getClassName(), name));
                }
            } catch (Throwable t) {
                LOGGER.warn("Unable to determine if class " + failedTest.getClassName() + " contains Spock @Unroll parameterizations", t);
            }
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
