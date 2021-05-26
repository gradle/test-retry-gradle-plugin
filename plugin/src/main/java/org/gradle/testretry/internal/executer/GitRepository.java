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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.gradle.testretry.internal.config.TestRetryTaskExtensionAdapter;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class GitRepository {

    private final List<String> modifiedFilesPaths;

    private GitRepository(List<String> modifiedFilesPaths) {
        this.modifiedFilesPaths = modifiedFilesPaths;
    }

    public boolean wasModified(String path) {
        return modifiedFilesPaths.contains(path);
    }

    static class NoRepository extends GitRepository {
        private NoRepository() {
            super(null);
        }

        @Override
        public boolean wasModified(String path) {
            return false;
        }
    }

    static GitRepository create(File rootDir, TestRetryTaskExtensionAdapter extension) {
        if (!extension.getModifiedTestRetry()) {
            return new NoRepository();
        }
        try (
            Git git = Git.open(rootDir)
        ) {
            Repository repository = git.getRepository();
            AbstractTreeIterator oldTree = getTreeIterator("refs/heads/master", repository);
            AbstractTreeIterator newTree = getTreeIterator("HEAD", repository);
            List<DiffEntry> diffs = git.diff()
                .setOldTree(oldTree)
                .setNewTree(newTree)
                .call();
            return new GitRepository(
                diffs.stream()
                    .map(DiffEntry::getNewPath)
                    .map(it -> { //src/test/java/acme/SuccessfulTests.java
                            final int firstSlash = it.indexOf('/');  //src/
                            final int secondSlash = it.indexOf('/', firstSlash + 1); //test/
                            final int thirdSlash = it.indexOf('/', secondSlash + 1); //java/
                            final int extensionIndex = it.lastIndexOf('.'); //.java
                            final String packageClass = it.substring(thirdSlash + 1, extensionIndex); // acme/SuccessfulTests
                            return packageClass.replace('/', '.'); //acme.SuccessfulTests
                        }
                    )
                    .collect(toList())
            );
        } catch (IOException | GitAPIException e) {
            return new NoRepository();
        }
    }

    private static AbstractTreeIterator getTreeIterator(String name, Repository repository)
        throws IOException {
        final ObjectId id = repository.resolve(name);
        if (id == null) {
            throw new IllegalArgumentException(name);
        }
        final CanonicalTreeParser p = new CanonicalTreeParser();
        try (ObjectReader or = repository.newObjectReader(); RevWalk walk = new RevWalk(repository)) {
            p.reset(or, walk.parseTree(id));
            return p;
        }
    }
}
