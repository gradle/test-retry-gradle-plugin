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

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;

import javax.annotation.Nullable;

final class TestDescriptorImpl implements TestDescriptorInternal {

    private final Object syntheticTestId;
    private final Object ownerBuildOperationId;
    private final String className;
    private final String testName;

    public TestDescriptorImpl(Object testId, Object ownerBuildOperationId, String className, String testName) {
        this.syntheticTestId = testId;
        this.ownerBuildOperationId = ownerBuildOperationId;
        this.className = className;
        this.testName = testName;
    }

    @Override
    public TestDescriptorInternal getParent() {
        return null;
    }

    @Override
    public Object getId() {
        return syntheticTestId;
    }

    @Nullable
    @Override
    public Object getOwnerBuildOperationId() {
        return ownerBuildOperationId;
    }

    @Nullable
    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getClassDisplayName() {
        return className;
    }

    @Override
    public String getName() {
        return testName;
    }

    @Override
    public String getDisplayName() {
        return testName;
    }

    @Override
    public boolean isComposite() {
        return false;
    }
}