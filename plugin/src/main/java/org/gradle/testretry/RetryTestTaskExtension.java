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

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public class RetryTestTaskExtension {
    /**
     * Whether the test task should fail when flaky tests ultimately pass.
     */
    private final Property<Boolean> failOnPassedAfterRetry;

    /**
     * Max number of times to retry, 0 disabled.
     */
    private final Property<Integer> maxRetries;

    /**
     * After this many discrete failed tests, stop retrying.
     */
    private final Property<Integer> maxFailures;

    @Inject
    public RetryTestTaskExtension(ObjectFactory objects) {
        this.failOnPassedAfterRetry = objects.property(Boolean.class);
        this.maxRetries = objects.property(Integer.class);
        this.maxFailures = objects.property(Integer.class);
    }

    public Property<Boolean> getFailOnPassedAfterRetry() {
        return failOnPassedAfterRetry;
    }

    public Property<Integer> getMaxRetries() {
        return maxRetries;
    }

    public Property<Integer> getMaxFailures() {
        return maxFailures;
    }
}
