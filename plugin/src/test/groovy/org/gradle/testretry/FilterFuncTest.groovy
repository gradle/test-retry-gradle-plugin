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
package org.gradle.testretry

import spock.lang.Unroll

import javax.annotation.Nullable

class FilterFuncTest extends AbstractGeneralPluginFuncTest {

    @Unroll
    def "can filter what is retried (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry {
                maxRetries = 2
                filter {
                    includeClasses.add("*Included*")
                    includeAnnotationClasses.add("*Included*")
                    excludeClasses.add("*Excluded*")
                    excludeAnnotationClasses.add("*Excluded*")
                }
            }
        """

        and:
        inheritedAnnotation("InheritedIncludedAnnotation")
        inheritedAnnotation("InheritedExcludedAnnotation")
        nonInheritedAnnotation("NonInheritedIncludedAnnotation")
        nonInheritedAnnotation("NonInheritedExcludedAnnotation")

        and:
        def noRetry = []
        def shouldRetry = []

        shouldRetry << test("BaseIncludedTest", null, "InheritedIncludedAnnotation")
        noRetry << test("BaseIncludedAndExcludedTest", "BaseIncludedTest", "InheritedExcludedAnnotation")
        noRetry << test("IncludedAndExcludedInheritedTest", "BaseIncludedAndExcludedTest")
        shouldRetry << test("IncludedNonInheritedTest", null, "NonInheritedIncludedAnnotation")
        noRetry << test("ExcludedTest", null, "NonInheritedIncludedAnnotation")
        noRetry << test("IncludeWithBadAnnotation", null, "InheritedExcludedAnnotation")

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        noRetry.each {
            assert result.output.count("acme.${it} > flakyTest FAILED") == 1
            assert result.output.count("acme.${it} > flakyTest PASSED") == 0
        }
        shouldRetry.each {
            assert result.output.count("acme.${it} > flakyTest FAILED") == 2
            assert result.output.count("acme.${it} > flakyTest PASSED") == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    private void nonInheritedAnnotation(String name) {
        file("src/test/java/acme/${name}.java") << """
            package acme;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            
            @Retention(RetentionPolicy.RUNTIME)
            @Target({ ElementType.TYPE })
            public @interface $name { }
        """
    }

    private void inheritedAnnotation(String name) {
        file("src/test/java/${name}.java") << """
            package acme;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            
            @Retention(RetentionPolicy.RUNTIME)
            @Target({ ElementType.TYPE })
            @java.lang.annotation.Inherited
            public @interface $name { }
        """
    }

    private String test(String name, @Nullable String superClass, String... annotations) {
        file("src/test/java/acme/${name}.java") << """
            package acme;
            ${annotations.collect { "@$it" }.join("\n")}
            public class $name ${superClass ? " extends $superClass" : ""} {
                @org.junit.Test
                public void flakyTest() {
                    ${flakyAssert(name, 2)}
                }
            }
        """
        return name
    }
}
