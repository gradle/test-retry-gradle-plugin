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

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.testing.Test;

/**
 * Allows configuring test retry mechanics.
 * <p>
 * This extension is added with the name 'retry' to all {@link Test} tasks.
 */
public interface TestRetryTaskExtension {

    /**
     * The name of the extension added to each test task.
     */
    String NAME = "retry";

    /**
     * Whether tests that initially fail and then pass on retry should fail the task.
     * <p>
     * This setting defaults to {@code false},
     * which results in the task not failing if all tests pass on retry.
     * <p>
     * This setting has no effect if {@link Test#getIgnoreFailures()} is set to true.
     *
     * @return whether tests that initially fails and then pass on retry should fail the task
     */
    Property<Boolean> getFailOnPassedAfterRetry();

    /**
     * The maximum number of times to retry an individual test.
     * <p>
     * This setting defaults to {@code 0}, which results in no retries.
     * Any value less than 1 disables retrying.
     *
     * @return the maximum number of times to retry an individual test
     */
    Property<Integer> getMaxRetries();

    /**
     * The maximum number of test failures that are allowed before retrying is disabled.
     * <p>
     * The count applies to each round of test execution.
     * For example, if maxFailures is 5 and 4 tests initially fail and then 3 again on retry,
     * this will not be considered too many failures and retrying will continue (if maxRetries {@literal >} 1).
     * If 5 or more tests were to fail initially then no retry would be attempted.
     * <p>
     * This setting defaults to {@code 0}, which results in no limit.
     * Any value less than 1 results in no limit.
     *
     * @return the maximum number of test failures that are allowed before retrying is disabled
     */
    Property<Integer> getMaxFailures();

}
