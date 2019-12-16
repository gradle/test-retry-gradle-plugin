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
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.testretry.TestRetryTaskExtension;
import org.gradle.util.VersionNumber;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class TestTaskConfigurer {

    private final ObjectFactory objectFactory;
    private final ProviderFactory providerFactory;
    private final Instantiator instantiator;
    private final ClassLoaderCache classLoaderCache;

    @Inject
    public TestTaskConfigurer(
        ObjectFactory objectFactory,
        ProviderFactory providerFactory,
        Instantiator instantiator,
        ClassLoaderCache classLoaderCache
    ) {
        this.objectFactory = objectFactory;
        this.providerFactory = providerFactory;
        this.instantiator = instantiator;
        this.classLoaderCache = classLoaderCache;
    }

    public void configureTestTask(Test test) {
        VersionNumber gradleVersion = VersionNumber.parse(test.getProject().getGradle().getGradleVersion());

        TestRetryTaskExtension extension;
        if (supportsGeneratedAbstractTypeImplementations(gradleVersion)) {
            extension = objectFactory.newInstance(TestRetryTaskExtension.class);
        } else {
            extension = objectFactory.newInstance(DefaultTestRetryTaskExtension.class);
        }

        TestRetryTaskExtensionAdapter adapter = new TestRetryTaskExtensionAdapter(
            providerFactory,
            extension,
            supportsPropertyConventions(gradleVersion)
        );

        test.getInputs().property("retry.failOnPassedAfterRetry", adapter.getFailOnPassedAfterRetryInput());

        test.getExtensions().add(TestRetryTaskExtension.class, TestRetryTaskExtension.NAME, extension);
        replaceTestExecuter(test, adapter);
    }

    private static boolean supportsGeneratedAbstractTypeImplementations(VersionNumber gradleVersion) {
        return gradleVersion.getMajor() == 5 ? gradleVersion.getMinor() >= 3 : gradleVersion.getMajor() > 5;
    }

    private static boolean supportsPropertyConventions(VersionNumber gradleVersion) {
        return gradleVersion.getMajor() == 5 ? gradleVersion.getMinor() >= 1 : gradleVersion.getMajor() > 5;
    }

    private void replaceTestExecuter(Test task, TestRetryTaskExtensionAdapter extension) {
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
