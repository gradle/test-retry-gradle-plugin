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
package org.gradle.testretry.testframework

import org.gradle.testretry.AbstractConfigCacheFuncTest
import org.junit.Assume
import org.spockframework.util.VersionNumber

class TestNGConfigCacheFuncTest extends AbstractConfigCacheFuncTest {
    @Override
    protected String buildConfiguration() {
        return """
            dependencies {
                testImplementation 'org.testng:testng:7.0.0'
            }
            test {
                useTestNG()
            }
        """
    }

    @Override
    String getTestAnnotation() {
        return "@org.testng.annotations.Test"
    }

    @Override
    void shouldTest(String gradleVersion) {
        super.shouldTest(gradleVersion)
        Assume.assumeTrue("TestNG and the configuration cache are not supported with Gradle $gradleVersion", isFrameworkSupported(gradleVersion))
    }

    // TestNG only works with config cache starting with 6.7
    boolean isFrameworkSupported(String gradleVersion) {
        // We use VersionNumber here so that we can match 6.7 nightlies
        return VersionNumber.parse(gradleVersion).with {
            it.major > 6 || (it.major == 6 && it.minor >= 7)
        }
    }
}
