/**
 * Copyright 2019 Gradle, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugins.testretry;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.reflect.Instantiator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TestRetryPlugin implements Plugin<Project> {

    private final ObjectFactory objectFactory;
    private final Instantiator instantiator;
    private final ClassLoaderCache classLoaderCache;

    @javax.inject.Inject
    public TestRetryPlugin(ObjectFactory objectFactory, Instantiator instantiator, ClassLoaderCache classLoaderCache){
        this.objectFactory = objectFactory;
        this.instantiator = instantiator;
        this.classLoaderCache = classLoaderCache;
    }

    @Override
    public void apply(Project project) {
        project.getTasks().withType(Test.class, this::configureTestTask);
    }

    private void configureTestTask(Test test) {
        RetryTestTaskExtension extension = test.getExtensions().create("retry", RetryTestTaskExtension.class, objectFactory);
        test.doFirst(t -> replaceTestExecuter(test, extension));
    }

    private void replaceTestExecuter(Test test, RetryTestTaskExtension extension) {
        try {
            Method createTestExecutor = Test.class.getDeclaredMethod("createTestExecuter");
            createTestExecutor.setAccessible(true);

            @SuppressWarnings("unchecked")
            TestExecuter<JvmTestExecutionSpec> delegate = (TestExecuter<JvmTestExecutionSpec>) createTestExecutor.invoke(test);

            Method setTestExecuter = Test.class.getDeclaredMethod("setTestExecuter", TestExecuter.class);
            setTestExecuter.setAccessible(true);
            setTestExecuter.invoke(test, new RetryTestExecuter(delegate, test,
                    extension.getMaxRetries().getOrElse(0), extension.getMaxFailures().getOrElse(Integer.MAX_VALUE),
                    instantiator, classLoaderCache));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
