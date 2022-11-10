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

import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.util.GradleVersion;

enum JvmTestExecutionSpecFactory {

    FACTORY_FOR_CURRENT_GRADLE_VERSION {
        @Override
        JvmTestExecutionSpec createExecutionSpec(TestFramework testFramework, JvmTestExecutionSpec source) {
            return source.copyWithTestFramework(testFramework);
        }
    },

    FACTORY_FOR_GRADLE_OLDER_THAN_V8 {
        @Override
        JvmTestExecutionSpec createExecutionSpec(TestFramework testFramework, JvmTestExecutionSpec source) {
            // This constructor is in Gradle 6.4+
            return new JvmTestExecutionSpec(
                testFramework,
                source.getClasspath(),
                source.getModulePath(),
                source.getCandidateClassFiles(),
                source.isScanForTestClasses(),
                source.getTestClassesDirs(),
                source.getPath(),
                source.getIdentityPath(),
                source.getForkEvery(),
                source.getJavaForkOptions(),
                source.getMaxParallelForks(),
                source.getPreviousFailedTestClasses()
            );
        }
    },

    FACTORY_FOR_GRADLE_OLDER_THAN_V6_4 {
        @Override
        JvmTestExecutionSpec createExecutionSpec(TestFramework testFramework, JvmTestExecutionSpec source) {
            // This constructor is in Gradle 4.7+
            return new JvmTestExecutionSpec(
                testFramework,
                source.getClasspath(),
                source.getCandidateClassFiles(),
                source.isScanForTestClasses(),
                source.getTestClassesDirs(),
                source.getPath(),
                source.getIdentityPath(),
                source.getForkEvery(),
                source.getJavaForkOptions(),
                source.getMaxParallelForks(),
                source.getPreviousFailedTestClasses()
            );

        }
    };

    abstract JvmTestExecutionSpec createExecutionSpec(TestFramework testFramework, JvmTestExecutionSpec source);

    static JvmTestExecutionSpec testExecutionSpecFor(TestFramework testFramework, JvmTestExecutionSpec source) {
        JvmTestExecutionSpecFactory factory = getInstance();
        return factory.createExecutionSpec(testFramework, source);
    }

    private static JvmTestExecutionSpecFactory getInstance() {
        if (gradleVersionIsAtLeast("8.0")) {
            return FACTORY_FOR_CURRENT_GRADLE_VERSION;
        } else if (gradleVersionIsAtLeast("6.4")) {
            return FACTORY_FOR_GRADLE_OLDER_THAN_V8;
        } else {
            return FACTORY_FOR_GRADLE_OLDER_THAN_V6_4;
        }
    }

    private static boolean gradleVersionIsAtLeast(String version) {
        return GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version(version)) >= 0;
    }

}
