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

import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.testretry.internal.TestName;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class JunitTestFrameworkStrategy extends BaseJunitTestFrameworkStrategy {

    public static final Logger LOGGER = LoggerFactory.getLogger(JunitTestFrameworkStrategy.class);

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

    @Override
    public TestFramework createRetrying(JvmTestExecutionSpec spec, Test testTask, Set<TestName> failedTests, Instantiator instantiator, ClassLoaderCache classLoaderCache) {
        DefaultTestFilter retriedTestFilter = new DefaultTestFilter();
        retriesWithSpockParametersRemoved(spec, failedTests).stream()
            .filter(failedTest -> failedTest.getClassName() != null)
            .forEach(failedTest -> {
                if (ERROR_SYNTHETIC_TEST_NAMES.contains(failedTest.getName())) {
                    retriedTestFilter.includeTestsMatching(failedTest.getClassName());
                } else if (isSpockStepwiseTest(spec, failedTest)) {
                    retriedTestFilter.includeTestsMatching(failedTest.getClassName());
                } else {
                    String strippedParameterName = failedTest.getName().replaceAll("\\[\\d+]$", "");
                    retriedTestFilter.includeTest(failedTest.getClassName(), strippedParameterName);
                    retriedTestFilter.includeTest(failedTest.getClassName(), failedTest.getName());
                }
            });

        return new JUnitTestFramework(testTask, retriedTestFilter);
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
