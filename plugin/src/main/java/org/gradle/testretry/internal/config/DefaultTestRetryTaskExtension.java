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
package org.gradle.testretry.internal.config;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.testretry.TestRetryTaskExtension;

import javax.inject.Inject;

public class DefaultTestRetryTaskExtension implements TestRetryTaskExtension {

    private final Property<Boolean> failOnPassedAfterRetry;
    private final Property<Integer> maxRetries;
    private final Property<Integer> maxFailures;

    @Inject
    public DefaultTestRetryTaskExtension(ObjectFactory objects) {
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
