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

import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.testretry.internal.executer.TestFrameworkTemplate;
import org.gradle.testretry.internal.executer.TestName;
import org.gradle.testretry.internal.executer.TestsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class TestNgTestFrameworkStrategy implements TestFrameworkStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestNgTestFrameworkStrategy.class);

    @Override
    public boolean isSyntheticFailure(String testName) {
        return false;
    }

    @Override
    public TestFramework createRetrying(TestFrameworkTemplate template, Set<TestName> failedTests) {
        DefaultTestFilter retriedTestFilter = new DefaultTestFilter();
        retriesWithTestNGDependentsAdded(template.testsReader, failedTests)
            .forEach(failedTest -> {
                if (failedTest.getName() == null) {
                    retriedTestFilter.includeTestsMatching(failedTest.getClassName());
                } else {
                    retriedTestFilter.includeTest(failedTest.getClassName(), failedTest.getName());
                }
            });

        TestNGTestFramework testFramework = createTestFramework(template, retriedTestFilter);
        copyTestNGOptions((TestNGOptions) template.task.getTestFramework().getOptions(), testFramework.getOptions());
        return testFramework;
    }

    private TestNGTestFramework createTestFramework(TestFrameworkTemplate template, DefaultTestFilter retriedTestFilter) {
        if (TestFrameworkStrategy.gradleVersionIsAtLeast("6.6")) {
            return new TestNGTestFramework(template.task, template.task.getClasspath(), retriedTestFilter, template.objectFactory);
        } else {
            try {
                ServiceRegistry serviceRegistry = ((ProjectInternal) template.task.getProject()).getServices();
                ClassLoaderCache classLoaderCache = serviceRegistry.get(ClassLoaderCache.class);
                Class<?> testNGTestFramework = TestNGTestFramework.class;
                @SuppressWarnings("JavaReflectionMemberAccess") final Constructor<?> constructor = testNGTestFramework.getConstructor(Test.class, DefaultTestFilter.class, Instantiator.class, ClassLoaderCache.class);
                return (TestNGTestFramework) constructor.newInstance(template.task, retriedTestFilter, template.instantiator, classLoaderCache);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void copyTestNGOptions(TestNGOptions source, TestNGOptions target) {
        target.setOutputDirectory(source.getOutputDirectory());
        target.setIncludeGroups(source.getIncludeGroups());
        target.setExcludeGroups(source.getExcludeGroups());
        target.setConfigFailurePolicy(source.getConfigFailurePolicy());
        target.setListeners(source.getListeners());
        target.setParallel(source.getParallel());
        target.setThreadCount(source.getThreadCount());
        target.setUseDefaultListeners(source.getUseDefaultListeners());
        target.setPreserveOrder(source.getPreserveOrder());
        target.setGroupByInstances(source.getGroupByInstances());

        target.setSuiteName(source.getSuiteName());
        target.setTestName(source.getTestName());
        target.setSuiteXmlFiles(source.getSuiteXmlFiles());
        target.setSuiteXmlBuilder(source.getSuiteXmlBuilder());
        target.setSuiteXmlWriter(source.getSuiteXmlWriter());
    }

    private static List<TestName> retriesWithTestNGDependentsAdded(TestsReader testsReader, Set<TestName> failedTests) {
        return failedTests.stream()
            .flatMap(failedTest -> {
                try {
                    Optional<TestNgClassVisitor> opt = testsReader.readTestClassDirClass(failedTest.getClassName(), TestNgClassVisitor::new);
                    return opt
                        .map(visitor ->
                            visitor.dependsOn(failedTest.getName())
                                .stream()
                                .map(method -> getTestNameFrom(failedTest.getClassName(), method))
                        )
                        .orElse(Stream.of(failedTest));
                } catch (Throwable t) {
                    LOGGER.warn("Unable to determine if class " + failedTest.getClassName() + " has TestNG dependent tests", t);
                    return Stream.of(failedTest);
                }
            })
            .collect(Collectors.toList());
    }

    @Override
    public TestName getTestNameFrom(TestDescriptor descriptor) {
        return getTestNameFrom(descriptor.getClassName(), descriptor.getName());
    }

    private static TestName getTestNameFrom(String className, String name) {
        return new TestName(className, stripParameterSegment(name));
    }

    private static String stripParameterSegment(String name) {
        return name.replaceAll("\\[[^)]+](\\([^)]*\\))+$", "");
    }
}
