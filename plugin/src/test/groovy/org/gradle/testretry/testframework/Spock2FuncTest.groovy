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

class Spock2FuncTest extends SpockFuncTest {

    boolean isRerunsParameterizedMethods() {
        false
    }

    @Override
    boolean canTargetInheritedMethods() {
        // https://github.com/spockframework/spock/issues/1235
        false
    }

    @Override
    boolean nonParameterizedMethodsCanHaveCustomIterationNames() {
        // // https://github.com/spockframework/spock/issues/1236
        false
    }

    @Override
    protected String initializationErrorSyntheticTestMethodName(String gradleVersion) {
        gradleVersion == "5.0" ? "classMethod" : "initializationError"
    }

    @Override
    protected String buildConfiguration() {
        return """
            dependencies {
                implementation 'org.spockframework:spock-core:2.0-M2-groovy-3.0'
            }
            test {
                useJUnitPlatform()
            }
        """
    }

}
