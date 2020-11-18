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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class TestNames {

    private final Map<String, Set<String>> map = new HashMap<>();

    void add(String className, String testName) {
        map.computeIfAbsent(className, ignored -> new HashSet<>()).add(testName);
    }

    void remove(String className, Predicate<? super String> predicate) {
        Set<String> testNames = map.get(className);
        if (testNames != null) {
            testNames.removeIf(predicate);
            if (testNames.isEmpty()) {
                map.remove(className);
            }
        }
    }

    boolean remove(String className, String testName) {
        Set<String> testNames = map.get(className);
        if (testNames == null) {
            return false;
        } else {
            if (testNames.remove(testName)) {
                if (testNames.isEmpty()) {
                    map.remove(className);
                }
                return true;
            } else {
                return false;
            }
        }
    }

    Stream<TestName> stream() {
        return map.entrySet()
            .stream()
            .flatMap(entry ->
                entry.getValue()
                    .stream()
                    .map(testName -> new TestName(entry.getKey(), testName))
            );
    }

    Set<TestName> toSet() {
        Set<TestName> set = new TreeSet<>();
        stream().forEach(set::add);
        return set;
    }

    boolean isEmpty() {
        return map.isEmpty();
    }

    public int size() {
        return (int) stream().count();
    }
}
