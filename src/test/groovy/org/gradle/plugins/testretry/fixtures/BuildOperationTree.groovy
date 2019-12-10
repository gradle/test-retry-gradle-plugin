/*
 * Copyright 2019 Gradle, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugins.testretry.fixtures

import com.google.common.base.Charsets
import com.google.common.collect.ImmutableMap
import com.google.common.io.Files
import com.google.common.io.LineProcessor
import groovy.json.JsonSlurper
import org.gradle.internal.UncheckedException

import static org.gradle.internal.Cast.uncheckedCast
import static org.gradle.internal.Cast.uncheckedNonnullCast

class BuildOperationTree {

    public final List<BuildOperationRecord> roots;
    public final Map<Long, BuildOperationRecord> records;

    BuildOperationTree(List<BuildOperationRecord> roots) {
        ImmutableMap.Builder<Long, BuildOperationRecord> records = ImmutableMap.builder();
        for (BuildOperationRecord record : roots) {
            visit(records, record);
        }
        this.roots = BuildOperationRecord.ORDERING.immutableSortedCopy(roots);
        this.records = records.build();
    }


    public static BuildOperationTree read(String basePath) {
        File logFile = logFile(basePath);
        List<BuildOperationRecord> roots = readLogToTreeRoots(logFile);
        return new BuildOperationTree(roots);
    }

    private static File logFile(String basePath) {
        return new File(basePath+"-log.txt");
    }

    private static List<BuildOperationRecord> readLogToTreeRoots(final File logFile) {
        try {
            final JsonSlurper slurper = new JsonSlurper();

            final List<BuildOperationRecord> roots = new ArrayList<>();
            final Map<Object, PendingOperation> pendings = new HashMap<>();
            final Map<Object, List<BuildOperationRecord>> childrens = new HashMap<>();

            Files.asCharSource(logFile, Charsets.UTF_8).readLines(new LineProcessor<Void>() {
                @Override
                public boolean processLine(@SuppressWarnings("NullableProblems") String line) {
                    Map<String, ?> map = uncheckedNonnullCast(slurper.parseText(line));
                    if (map.containsKey("startTime")) {
                        SerializedOperationStart serialized = new SerializedOperationStart(map);
                        pendings.put(serialized.id, new PendingOperation(serialized));
                        childrens.put(serialized.id, new LinkedList<>());
                    } else if (map.containsKey("time")) {
                        SerializedOperationProgress serialized = new SerializedOperationProgress(map);
                        PendingOperation pending = pendings.get(serialized.id);
                        assert pending != null: "did not find owner of progress event with ID " + serialized.id;
                        pending.progress.add(serialized);
                    } else {
                        SerializedOperationFinish finish = new SerializedOperationFinish(map);

                        PendingOperation pending = pendings.remove(finish.id);
                        assert pending != null;

                        List<BuildOperationRecord> children = childrens.remove(finish.id);
                        assert children != null;

                        SerializedOperationStart start = pending.start;

                        Map<String, ?> detailsMap = uncheckedCast(start.details);
                        Map<String, ?> resultMap = uncheckedCast(finish.result);

                        List<BuildOperationRecord.Progress> progresses = new ArrayList<>();
                        for (SerializedOperationProgress progress : pending.progress) {
                            Map<String, ?> progressDetailsMap = uncheckedCast(progress.details);
                            progresses.add(new BuildOperationRecord.Progress(
                                progress.time,
                                progressDetailsMap,
                                progress.detailsClassName
                            ));
                        }

                        BuildOperationRecord record = new BuildOperationRecord(
                            start.id,
                            start.parentId,
                            start.displayName,
                            start.startTime,
                            finish.endTime,
                            detailsMap == null ? null : Collections.unmodifiableMap(detailsMap),
                            start.detailsClassName,
                            resultMap == null ? null : Collections.unmodifiableMap(resultMap),
                            finish.resultClassName,
                            finish.failureMsg,
                            progresses,
                            BuildOperationRecord.ORDERING.immutableSortedCopy(children)
                        );

                        if (start.parentId == null) {
                            roots.add(record);
                        } else {
                            List<BuildOperationRecord> parentChildren = childrens.get(start.parentId);
                            assert parentChildren != null: "parentChildren != null '" + line + "' from " + logFile;
                            parentChildren.add(record);
                        }
                    }

                    return true;
                }

                @Override
                public Void getResult() {
                    return null;
                }
            });

            assert pendings.isEmpty();

            return roots;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

    }


    private void visit(ImmutableMap.Builder<Long, BuildOperationRecord> records, BuildOperationRecord record) {
        records.put(record.id, record);
        for (BuildOperationRecord child : record.children) {
            visit(records, child);
        }
    }

    static class PendingOperation {

        final SerializedOperationStart start;

        final List<SerializedOperationProgress> progress = new ArrayList<>();

        PendingOperation(SerializedOperationStart start) {
            this.start = start;
        }
    }

}


