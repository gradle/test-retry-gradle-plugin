/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.util.GradleVersion

class Spock2FuncTest extends SpockBaseJunit5FuncTest {

    @Override
    boolean isRerunsParameterizedMethods() {
        true
    }

    @Override
    boolean canTargetInheritedMethods(String gradleVersion) {
        GradleVersion.version(gradleVersion) >= GradleVersion.version("7.0")
    }

    @Override
    protected String beforeClassErrorTestMethodName(String gradleVersion) {
        gradleVersion == "5.0" ? "classMethod" : "initializationError"
    }

    @Override
    protected String afterClassErrorTestMethodName(String gradleVersion) {
        gradleVersion == "5.0" ? "classMethod" : "executionError"
    }

    @Override
    protected String buildConfiguration() {
        return """
            dependencies {
                implementation 'org.spockframework:spock-core:2.3-groovy-3.0'
            }
            test {
                useJUnitPlatform()
            }
        """
    }

    @Override
    protected String contextualTestExtension() {
        """
            package acme

            import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
            import org.spockframework.runtime.model.SpecInfo

            class ContextualTestExtension extends AbstractAnnotationDrivenExtension<ContextualTest> {

                @Override
                void visitSpecAnnotation(ContextualTest annotation, SpecInfo spec) {

                    spec.features.each { feature ->
                        feature.reportIterations = true
                        if (feature.parameterized) {
                            def currentNameProvider = feature.iterationNameProvider
                            feature.iterationNameProvider = {
                                def defaultName = currentNameProvider != null ? currentNameProvider.getName(it) : feature.name
                                defaultName + " [suffix]"
                            }
                        } else {
                            feature.displayName += " [suffix]"
                        }
                    }
                }
            }
        """
    }
}
