package org.gradle.testretry.internal.executer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

final class TestNames {

    private final Map<String, Set<String>> map = new HashMap<>();

    void add(String className, String testName) {
        map.computeIfAbsent(className, ignored -> new HashSet<>()).add(testName);
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
