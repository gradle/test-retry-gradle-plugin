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

import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.testretry.TestRetryTaskExtension;
import org.gradle.util.VersionNumber;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.testretry.internal.config.TestTaskConfigurer.supportsPropertyConventions;

public final class TestRetryTaskExtensionAdapter {

    // for testing only
    public static final String SIMULATE_NOT_RETRYABLE_PROPERTY = "__org_gradle_testretry_simulate_not_retryable";

    private static final int DEFAULT_MAX_RETRIES = 0;
    private static final int DEFAULT_MAX_FAILURES = 0;
    private static final boolean DEFAULT_FAIL_ON_PASSED_AFTER_RETRY = false;

    private final ProviderFactory providerFactory;
    private final TestRetryTaskExtension extension;
    private final boolean simulateNotRetryableTest;
    private final boolean useConventions;

    public TestRetryTaskExtensionAdapter(
        ProviderFactory providerFactory,
        TestRetryTaskExtension extension,
        VersionNumber gradleVersion
    ) {
        this.providerFactory = providerFactory;
        this.extension = extension;
        this.simulateNotRetryableTest = Boolean.getBoolean(SIMULATE_NOT_RETRYABLE_PROPERTY);
        this.useConventions = supportsPropertyConventions(gradleVersion);

        initialize(extension, this.useConventions);
    }

    private static void initialize(TestRetryTaskExtension extension, boolean gradle51OrLater) {
        if (gradle51OrLater) {
            extension.getMaxRetries().convention(DEFAULT_MAX_RETRIES);
            extension.getMaxFailures().convention(DEFAULT_MAX_FAILURES);
            extension.getFailOnPassedAfterRetry().convention(DEFAULT_FAIL_ON_PASSED_AFTER_RETRY);
            extension.getFilter().getIncludeClasses().convention(Collections.emptySet());
            extension.getFilter().getIncludeAnnotationClasses().convention(Collections.emptySet());
            extension.getFilter().getExcludeClasses().convention(Collections.emptySet());
            extension.getFilter().getExcludeAnnotationClasses().convention(Collections.emptySet());
        } else {
            // https://github.com/gradle/gradle/issues/7485
            extension.getFilter().getIncludeClasses().empty();
            extension.getFilter().getIncludeAnnotationClasses().empty();
            extension.getFilter().getExcludeClasses().empty();
            extension.getFilter().getExcludeAnnotationClasses().empty();
        }
    }

    Callable<Provider<Boolean>> getFailOnPassedAfterRetryInput() {
        if (useConventions) {
            return extension::getFailOnPassedAfterRetry;
        } else {
            return () -> {
                if (extension.getFailOnPassedAfterRetry().isPresent()) {
                    return extension.getFailOnPassedAfterRetry();
                } else {
                    return providerFactory.provider(TestRetryTaskExtensionAdapter.this::getFailOnPassedAfterRetry);
                }
            };
        }
    }

    public boolean getFailOnPassedAfterRetry() {
        return read(extension.getFailOnPassedAfterRetry(), DEFAULT_FAIL_ON_PASSED_AFTER_RETRY);
    }

    public int getMaxRetries() {
        return read(extension.getMaxRetries(), DEFAULT_MAX_RETRIES);
    }

    public int getMaxFailures() {
        return read(extension.getMaxFailures(), DEFAULT_MAX_FAILURES);
    }

    public Set<String> getIncludeClasses() {
        return read(extension.getFilter().getIncludeClasses(), Collections.emptySet());
    }

    public Set<String> getIncludeAnnotationClasses() {
        return read(extension.getFilter().getIncludeAnnotationClasses(), Collections.emptySet());
    }

    public Set<String> getExcludeClasses() {
        return read(extension.getFilter().getExcludeClasses(), Collections.emptySet());
    }

    public Set<String> getExcludeAnnotationClasses() {
        return read(extension.getFilter().getExcludeAnnotationClasses(), Collections.emptySet());
    }

    public boolean getSimulateNotRetryableTest() {
        return simulateNotRetryableTest;
    }

    private <T> T read(Property<T> property, T defaultValue) {
        return useConventions ? property.get() : property.getOrElse(defaultValue);
    }

    private <T> Set<T> read(SetProperty<T> property, Set<T> defaultValue) {
        return useConventions ? property.get() : property.getOrElse(defaultValue);
    }

}
