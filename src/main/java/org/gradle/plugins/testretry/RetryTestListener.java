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

import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RetryTestListener implements TestListener {

    private List<TestDescriptor> failedTests = new CopyOnWriteArrayList<>();

    @Override
    public void beforeSuite(TestDescriptor testDescriptor) {

    }

    @Override
    public void afterSuite(TestDescriptor testDescriptor, TestResult testResult) {

    }

    @Override
    public void beforeTest(TestDescriptor testDescriptor) {

    }

    @Override
    public void afterTest(TestDescriptor testDescriptor, TestResult testResult) {
        if(testResult.getResultType() == TestResult.ResultType.FAILURE) {
            failedTests.add(testDescriptor);
        }
    }

    public List<TestDescriptor> getFailedTests() {
        return failedTests;
    }

    public void reset() {
        failedTests.clear();
    }
}
