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

import org.gradle.testretry.internal.executer.TestFilterBuilder;
import org.gradle.testretry.internal.executer.TestNames;
import org.gradle.testretry.internal.testsreader.TestsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    public boolean isLifecycleFailureTest(TestsReader testsReader, String className, String testName) {
        return ERROR_SYNTHETIC_TEST_NAMES.contains(testName);
    }

    protected void addFilters(TestFilterBuilder filters, TestsReader testsReader, TestNames failedTests, boolean canRunParameterizedSpockMethods) {
        failedTests.stream()
            .forEach(entry -> {
                String className = entry.getKey();
                Set<String> tests = entry.getValue();

                if (tests.stream().anyMatch(ERROR_SYNTHETIC_TEST_NAMES::contains)) {
                    filters.clazz(className);
                    return;
                }

                if (processSpockTest(filters, testsReader, canRunParameterizedSpockMethods, className, tests)) {
                    return;
                }

                tests.forEach(name -> addPotentiallyParameterizedSuffixed(filters, className, name));
            });
    }

    private boolean processSpockTest(TestFilterBuilder filters, TestsReader testsReader, boolean canRunParameterizedSpockMethods, String className, Set<String> tests) {
        if (isSpockStepwiseTest(testsReader, className)) {
            filters.clazz(className);
            return true;
        }

        try {
            Optional<Map<String, List<String>>> resultOpt = testsReader.readTestClassDirClass(className, () -> new SpockParameterClassVisitor(tests, testsReader));
            if (resultOpt.isPresent()) {
                Map<String, List<String>> result = resultOpt.get();
                if (result.isEmpty()) {
                    return false; // not a spec
                }

                if (canRunParameterizedSpockMethods) {
                    result.forEach((test, matches) -> {
                        if (matches.isEmpty()) {
                            addPotentiallyParameterizedSuffixed(filters, className, test);
                        } else {
                            matches.forEach(match -> filters.test(className, match));
                        }
                    });
                } else {
                    boolean allLiteralMethodMatches = result.entrySet()
                        .stream()
                        .allMatch(e2 -> e2.getValue().size() == 1 && e2.getValue().get(0).equals(e2.getKey()));

                    if (allLiteralMethodMatches) {
                        tests.forEach(test -> filters.test(className, test));
                    } else {
                        filters.clazz(className);
                    }
                }

                return true;
            }
        } catch (Throwable t) {
            LOGGER.warn("Unable to determine if class " + className + " contains Spock @Unroll parameterizations", t);
        }
        return false;
    }

    private void addPotentiallyParameterizedSuffixed(TestFilterBuilder filters, String className, String name) {
        // It's a common pattern to add all the parameters on the end of a literal method name with []
        String strippedParameterName = name.replaceAll("(?:\\([^)]*?\\)|\\[[^]]*?])*$", "");
        filters.test(className, strippedParameterName);
        filters.test(className, name);
    }

    private static boolean isSpockStepwiseTest(TestsReader testsReader, String className) {
        try {
            return testsReader.readTestClassDirClass(className, SpockStepwiseClassVisitor::new)
                .orElse(false);
        } catch (Throwable t) {
            LOGGER.warn("Unable to determine if class " + className + " is a Spock @Stepwise test", t);
            return false;
        }
    }

}
