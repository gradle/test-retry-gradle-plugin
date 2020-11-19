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

class SpockViaJUnitVintageFuncTest extends SpockFuncTest {

    boolean isRerunsParameterizedMethods() {
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
                implementation "org.codehaus.groovy:groovy-all:2.5.8", {
                    exclude group: "org.junit.jupiter"
                }
                testImplementation "org.spockframework:spock-core:1.3-groovy-2.5"
                testImplementation "org.junit.jupiter:junit-jupiter-api:5.6.2"
                testRuntimeOnly "org.junit.vintage:junit-vintage-engine:5.6.2"
            }

            test {
                useJUnitPlatform()
            }
        """
    }

}
