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

package org.gradle.testretry;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.testretry.internal.DefaultTestRetryTaskExtension;
import org.gradle.testretry.internal.RetryTestExecuter;
import org.gradle.testretry.internal.RetryTestFrameworkGenerator;
import org.gradle.util.VersionNumber;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@SuppressWarnings("UnstableApiUsage")
public class TestRetryPlugin implements Plugin<Project> {

    private final ObjectFactory objectFactory;
    private final Instantiator instantiator;
    private final ClassLoaderCache classLoaderCache;

    @Inject
    public TestRetryPlugin(ObjectFactory objectFactory, Instantiator instantiator, ClassLoaderCache classLoaderCache) {
        this.objectFactory = objectFactory;
        this.instantiator = instantiator;
        this.classLoaderCache = classLoaderCache;
    }

    @Override
    public void apply(Project project) {
        project.getTasks().withType(Test.class, this::configureTestTask);
    }

    private void configureTestTask(Test test) {
        VersionNumber gradleVersion = VersionNumber.parse(test.getProject().getGradle().getGradleVersion());

        TestRetryTaskExtension extension;
        if (supportsGeneratedAbstractTypeImplementations(gradleVersion)) {
            extension = objectFactory.newInstance(TestRetryTaskExtension.class);
        } else {
            extension = objectFactory.newInstance(DefaultTestRetryTaskExtension.class);
        }

        if (supportsPropertyConventions(gradleVersion)) {
            setDefaults(extension);
        }

        test.getInputs().property("retry.failOnPassedAfterRetry", extension.getFailOnPassedAfterRetry());

        test.getExtensions().add(TestRetryTaskExtension.class, TestRetryTaskExtension.NAME, extension);
        replaceTestExecuter(test, extension);
    }

    private void setDefaults(TestRetryTaskExtension extension) {
        extension.getMaxRetries().convention(DefaultTestRetryTaskExtension.DEFAULT_MAX_RETRIES);
        extension.getMaxFailures().convention(DefaultTestRetryTaskExtension.DEFAULT_MAX_FAILURES);
        extension.getFailOnPassedAfterRetry().convention(DefaultTestRetryTaskExtension.DEFAULT_FAIL_ON_PASSED_AFTER_RETRY);
    }

    private static boolean supportsGeneratedAbstractTypeImplementations(VersionNumber gradleVersion) {
        return gradleVersion.getMajor() == 5 ? gradleVersion.getMinor() >= 3 : gradleVersion.getMajor() > 5;
    }

    private static boolean supportsPropertyConventions(VersionNumber gradleVersion) {
        return gradleVersion.getMajor() == 5 ? gradleVersion.getMinor() >= 1 : gradleVersion.getMajor() > 5;
    }

    private void replaceTestExecuter(Test task, TestRetryTaskExtension extension) {
        try {
            Method createTestExecutor = Test.class.getDeclaredMethod("createTestExecuter");
            createTestExecutor.setAccessible(true);

            @SuppressWarnings("unchecked")
            TestExecuter<JvmTestExecutionSpec> delegate = (TestExecuter<JvmTestExecutionSpec>) createTestExecutor.invoke(task);

            Method setTestExecuter = Test.class.getDeclaredMethod("setTestExecuter", TestExecuter.class);
            setTestExecuter.setAccessible(true);

            setTestExecuter.invoke(task, new RetryTestExecuter(
                task,
                extension,
                delegate,
                new RetryTestFrameworkGenerator(classLoaderCache, instantiator)
            ));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
