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
package org.gradle.plugins.testretry.fixtures

import org.gradle.api.Action
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.operations.BuildOperationType

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.regex.Pattern

/**
 * This has been copied from the gradle build tool
 * test fixtures repo
 * */
class BuildOperationsFixture {

    private BuildOperationTree operations

    BuildOperationsFixture(String path) {
        operations = BuildOperationTree.read(path)
    }

    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T extends BuildOperationType<?, ?>> List<BuildOperationRecord> all(Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.satisfyAll()) {
        def detailsType = BuildOperationTypes.detailsType(type)
        operations.records.values().findAll {
            def clazz = tryLoadClazz(it)
            it.detailsClassName && clazz && detailsType.isAssignableFrom(clazz)
        }.toList()
    }

    private Class<?> tryLoadClazz(BuildOperationRecord it) {
        try {
            return getClass().getClassLoader().loadClass(it.detailsClassName)
        }catch(Exception e) {
            return null;
        }
    }

    List<BuildOperationRecord> all(Pattern displayName) {
        operations.records.values().findAll { it.displayName ==~ displayName }
    }

    BuildOperationRecord only(String displayName) {
        only(Pattern.compile(Pattern.quote(displayName)))
    }

    BuildOperationRecord only(Pattern displayName) {
        def records = all(displayName)
        assert records.size() == 1: "Error matching pattern: $displayName"
        records.first()
    }

    void none(String displayName) {
        none(Pattern.compile(Pattern.quote(displayName)))
    }

    void none(Pattern displayName) {
        def records = all(displayName)
        assert records.size() == 0
    }

    @SuppressWarnings(["GrMethodMayBeStatic", "GrUnnecessaryPublicModifier"])
    public <T extends BuildOperationType<?, ?>> List<BuildOperationRecord> search(BuildOperationRecord parent, Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.SATISFIES_ALL) {
        def detailsType = BuildOperationTypes.detailsType(type)
        Spec<BuildOperationRecord> typeSpec = {
            it.detailsType && detailsType.isAssignableFrom(it.detailsType)
        }
        search(parent, Specs.intersect(typeSpec, predicate))
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    List<BuildOperationRecord> search(BuildOperationRecord parent, Spec<? super BuildOperationRecord> predicate = Specs.SATISFIES_ALL) {
        def matches = []
        walk(parent) {
            if (predicate.isSatisfiedBy(it)) {
                matches << it
            }
        }
        matches
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    void walk(BuildOperationRecord parent, Action<? super BuildOperationRecord> action) {
        def search = new ConcurrentLinkedQueue<BuildOperationRecord>(parent.children)

        def operation = search.poll()
        while (operation != null) {
            action.execute(operation)
            search.addAll(operation.children)
            operation = search.poll()
        }
    }

    static class TimePoint implements Comparable<TimePoint> {
        private final boolean end
        private final long time
        private final BuildOperationRecord operation

        TimePoint(BuildOperationRecord operation, long time) {
            this(operation, time, false)
        }

        TimePoint(BuildOperationRecord operation, long time, boolean end) {
            this.operation = operation
            this.time = time
            this.end = end
        }

        @Override
        int compareTo(TimePoint o) {
            if (o.time > time) {
                return -1
            } else if (o.time < time) {
                return 1
            } else {
                if (end && o.end) {
                    return 0
                } else if (end) {
                    return -1
                } else {
                    return 1
                }
            }
        }

        @Override
        String toString() {
            if (end) {
                time + "E"
            } else {
                time + "S"
            }
        }
    }

    /**
     * Asserts that no more than maximumConcurrentOperations of the given type of build operation are executing at the same time.
     *
     * @param type type of build operation
     * @param maximumConcurrentOperations maximum concurrent operations allowed
     * @param concurrencyExpected whether or not to expect _any_ concurrency
     */
    void assertConcurrentOperationsDoNotExceed(Class<BuildOperationType> type, int maximumConcurrentOperations, boolean concurrencyExpected = false) {
        int maxConcurrency = getMaximumConcurrentOperations(type)
        assert maxConcurrency <= maximumConcurrentOperations
        if (concurrencyExpected) {
            assert maxConcurrency > 1: "No operations were executed concurrently"
        }
    }

    void assertConcurrentOperationsExecuted(Class<BuildOperationType> type) {
        assert getMaximumConcurrentOperations(type) > 1: "No operations were executed concurrently"
    }

    int getMaximumConcurrentOperations(Class<BuildOperationType> type) {
        def highWaterPoint = 0
        def allOperations = all(type)

        List<TimePoint> points = []

        allOperations.each {
            points.add(new TimePoint(it, it.startTime))
            points.add(new TimePoint(it, it.endTime, true))
        }

        def concurrentOperations = []
        points.sort().each {
            if (it.end) {
                concurrentOperations.remove(it.operation)
            } else {
                if ((it.operation.endTime - it.operation.startTime) > 0) {
                    concurrentOperations.add(it.operation)
                }
            }
            if (concurrentOperations.size() > highWaterPoint) {
                highWaterPoint = concurrentOperations.size()
            }
        }
        return highWaterPoint
    }

    /**
     * Return a list of operations (possibly empty) that executed concurrently with the given operation.
     */
    List<BuildOperationRecord> getOperationsConcurrentWith(Class<BuildOperationType> type, BuildOperationRecord operation) {
        def concurrentOperations = []
        all(type).each { candidate ->
            if (candidate != operation && candidate.startTime < operation.endTime && candidate.endTime > operation.startTime) {
                concurrentOperations << candidate
            }
        }
        return concurrentOperations
    }

    void debugTree(
        Spec<? super BuildOperationRecord> predicate = Specs.SATISFIES_ALL,
        Spec<? super BuildOperationRecord> progressPredicate = Specs.SATISFIES_ALL
    ) {
        operations.roots.each { debugOpTree(it, 0, predicate, progressPredicate) }
    }

    private void debugOpTree(
        BuildOperationRecord op,
        int level,
        Spec<? super BuildOperationRecord> predicate,
        Spec<? super BuildOperationRecord> progressPredicate
    ) {
        if (predicate.isSatisfiedBy(op)) {
            println "${'  ' * level}(${op.displayName}, id: $op.id${op.detailsType ? ", details type: ${simpleClassName(op.detailsType)}" : ''})${op.details ? " $op.details" : ''}"
            if (progressPredicate.isSatisfiedBy(op)) {
                op.progress.each { p ->
                    def repr = p.hasDetailsOfType(StyledTextOutputEvent) ? p.details.spans*.text.join('') : "$p.detailsType.simpleName ${p.details?.toString() ?: ''}\n"
                    print "${'  ' * (level + 1)} $repr"
                }
            }
            op.children.each { debugOpTree(it, level + 1, predicate, progressPredicate) }
        }
    }

    private static String simpleClassName(Class<?> detailsType) {
        if (!detailsType) {
            return null
        } else {
            // Class.simpleName returns "" for certain anonymous classes and unhelpful things like "Details" for our op interfaces
            String clsName = detailsType.interfaces.length == 0 ? detailsType.name : detailsType.interfaces.first().name
            clsName.substring(clsName.lastIndexOf('.') + 1)
        }
    }
}
