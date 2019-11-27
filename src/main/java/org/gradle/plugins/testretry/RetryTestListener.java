package org.gradle.plugins.testretry;

import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;

import java.util.ArrayList;
import java.util.List;

public class RetryTestListener implements TestListener {

    private List<TestDescriptor> failedTests = new ArrayList<TestDescriptor>();

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
