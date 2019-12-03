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

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public class RetryTestTaskExtension {

    /**
     * Max number of times to retry, 0 disabled.
     */
    private final Property<Integer> maxRetries;

    /**
     * After this many discrete failed tests, stop retrying.
     */
    private final Property<Integer> maxFailures;

    @javax.inject.Inject
    public RetryTestTaskExtension(ObjectFactory objects) {
        maxRetries = objects.property(Integer.class);
        maxFailures = objects.property(Integer.class);
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries.set(maxRetries);
    }

    public void setMaxFailures(int maxFailures) {
        this.maxFailures.set(maxFailures);
    }

    public Property<Integer> getMaxRetries() {
        return maxRetries;
    }

    public Property<Integer> getMaxFailures() {
        return maxFailures;
    }
}
