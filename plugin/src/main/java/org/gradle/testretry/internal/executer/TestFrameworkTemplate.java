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
package org.gradle.testretry.internal.executer;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.testretry.internal.config.TestRetryTaskExtensionAdapter;
import org.gradle.testretry.internal.testsreader.TestsReader;

public class TestFrameworkTemplate {

    public final Test task;
    public final TestRetryTaskExtensionAdapter extension;
    public final Instantiator instantiator;
    public final ObjectFactory objectFactory;
    public final TestsReader testsReader;

    public TestFrameworkTemplate(Test task, TestRetryTaskExtensionAdapter extension, Instantiator instantiator, ObjectFactory objectFactory) {
        this.task = task;
        this.extension = extension;
        this.instantiator = instantiator;
        this.objectFactory = objectFactory;
        this.testsReader = new TestsReader(task.getTestClassesDirs().getFiles(), task.getClasspath());
    }

    public TestFilterBuilder filterBuilder() {
        return new TestFilterBuilder(extension.getRetryEntireTestClass());
    }
}
