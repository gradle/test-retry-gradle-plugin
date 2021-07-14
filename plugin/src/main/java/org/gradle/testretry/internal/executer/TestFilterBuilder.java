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

import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;

public final class TestFilterBuilder {

    private final DefaultTestFilter filter = new DefaultTestFilter();
    private final boolean alwaysIncludeEntireTestClass;

    public TestFilterBuilder(boolean alwaysIncludeEntireTestClass) {
        this.alwaysIncludeEntireTestClass = alwaysIncludeEntireTestClass;
    }

    public void test(String className, String methodName) {
        if (alwaysIncludeEntireTestClass) {
            clazz(className);
        } else {
            filter.includeTest(className, methodName);
        }
    }

    public void clazz(String className) {
        filter.includeTestsMatching(className); // don't use includeTest with null method - it doesn't work < Gradle 6
    }

    public DefaultTestFilter build() {
        return filter;
    }
}
