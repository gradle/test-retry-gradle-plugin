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
package org.gradle.testretry.internal.filter;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RetryFilter {

    private final AnnotationInspector annotationInspector;

    private final Set<Pattern> includeClasses;
    private final Set<Pattern> includeAnnotationClasses;
    private final Set<Pattern> excludeClasses;
    private final Set<Pattern> excludeAnnotationClasses;

    public RetryFilter(
        AnnotationInspector annotationInspector,
        Set<String> includeClasses,
        Set<String> includeAnnotationClasses,
        Set<String> excludeClasses,
        Set<String> excludeAnnotationClasses
    ) {
        this.annotationInspector = annotationInspector;
        this.includeClasses = toPatterns(includeClasses);
        this.includeAnnotationClasses = toPatterns(includeAnnotationClasses);
        this.excludeClasses = toPatterns(excludeClasses);
        this.excludeAnnotationClasses = toPatterns(excludeAnnotationClasses);
    }

    public boolean canRetry(String className) {
        if (!includeClasses.isEmpty()) {
            boolean matchesNoIncludeClasses = includeClasses.stream()
                .noneMatch(pattern -> pattern.matcher(className).matches());

            if (matchesNoIncludeClasses) {
                return false;
            }
        }

        if (excludeClasses.stream().anyMatch(pattern -> pattern.matcher(className).matches())) {
            return false;
        }

        Set<String> annotations = null;
        if (!includeAnnotationClasses.isEmpty()) {
            annotations = annotationInspector.getClassAnnotations(className);
            boolean hasNoIncludeAnnotation = annotations.stream()
                .noneMatch(annotation ->
                    includeAnnotationClasses.stream()
                        .anyMatch(pattern -> pattern.matcher(annotation).matches())
                );

            if (!hasNoIncludeAnnotation) {
                return false;
            }
        }

        if (!excludeAnnotationClasses.isEmpty()) {
            annotations = annotations == null ? annotationInspector.getClassAnnotations(className) : annotations;
            boolean hasAnyExcludeAnnotation = annotations.stream()
                .anyMatch(annotation ->
                    includeAnnotationClasses.stream()
                        .anyMatch(pattern -> pattern.matcher(annotation).matches())
                );

            return !hasAnyExcludeAnnotation;
        }

        return true;
    }

    private static Set<Pattern> toPatterns(Set<String> strings) {
        return strings.stream()
            .map(string ->
                Arrays.stream(string.split("\\*"))
                    .map(Pattern::quote)
                    .collect(Collectors.joining(".*?"))
                    + (string.endsWith("*") ? ".*?" : "")
            )
            .map(Pattern::compile)
            .collect(Collectors.toSet());
    }
}
