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

import groovy.json.StringEscapeUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import spock.lang.Unroll

class ModifiedGroovyFuncTest extends AbstractGeneralPluginFuncTest {
    private Git git

    @Unroll
    def "has no effect when is disabled (gradle version #gradleVersion)"() {
        when:
        buildFile << """
            test.retry.modifiedTestRetry = false
        """

        successfulTest()

        then:
        def result = gradleRunner(gradleVersion).build()

        and:
        result.output.count('PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "has no effect when is enabled in none git context (gradle version #gradleVersion)"() {
        when:
        buildFile << """
            test.retry.modifiedTestRetry = true
            test.retry.maxRetries = 1
        """

        successfulTest()

        then:
        def result = gradleRunner(gradleVersion).build()

        and:
        result.output.count('PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "has no effect for untracked files (gradle version #gradleVersion)"() {
        when:
        buildFile << """
            test.retry.modifiedTestRetry = true
            test.retry.maxRetries = 1
        """

        initGit()

        successfulTest()

        then:
        def result = gradleRunner(gradleVersion).build()

        and:
        result.output.count('PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "has no effect for staged files (gradle version #gradleVersion)"() {
        when:
        buildFile << """
            test.retry.modifiedTestRetry = true
            test.retry.maxRetries = 1
        """

        initGit()

        successfulTest()

        stageSuccessfulTest()

        then:
        def result = gradleRunner(gradleVersion).build()

        and:
        result.output.count('PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "retries all commited test cases based on diff with master (gradle version #gradleVersion)"() {
        when:
        buildFile << """
            test.retry.modifiedTestRetry = true
            test.retry.maxRetries = 1
        """

        initGit()

        successfulTest()

        commitInNewBranch()

        then:
        def result = gradleRunner(gradleVersion).build()

        and:
        result.output.count('PASSED') == 2

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "has no effect when the very first test fails (gradle version #gradleVersion)"() {
        when:
        buildFile << """
            test.retry.modifiedTestRetry = true
            test.retry.maxRetries = 10
        """

        initGit()

        failedTest()

        commitInNewBranch()

        then:
        def result = gradleRunner(gradleVersion).buildAndFail()

        and:
        // 1 individual tests FAILED + 1 overall task FAILED + 1 overall build FAILED
        result.output.count('FAILED') == 1 + 1 + 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "retries until the first fail (gradle version #gradleVersion)"() {
        when:
        buildFile << """
            test.retry.modifiedTestRetry = true
            test.retry.maxRetries = 10
        """

        initGit()

        flakyOnRetry()

        commitInNewBranch()

        then:
        def result = gradleRunner(gradleVersion).buildAndFail()

        and:
        result.output.count('PASSED') == 1
        result.output.count('FAILED') == 1 + 1 + 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    void flakyOnRetry() {
        writeTestSource flakyOnNthExecutionAssertClass()
        writeTestSource("""
            package acme;

            public class FlakyTests {
                @org.junit.Test
                public void flaky() {
                    ${flakyAssert()}
                }
            }
        """)
    }

    String flakyAssert(String id = "id", int failures = 1) {
        return "acme.FlakyOnNthExecutionAssert.flakyAssert(\"${StringEscapeUtils.escapeJava(id)}\", $failures);"
    }

    String flakyOnNthExecutionAssertClass() {
        """
            package acme;

            import java.nio.file.*;

            public class FlakyOnNthExecutionAssert {
                public static void flakyAssert(String id, int failures) {
                    Path marker = Paths.get("build/marker.file." + id);
                    try {
                        if (Files.exists(marker)) {
                            int counter = Integer.parseInt(new String(Files.readAllBytes(marker)));
                            if (++counter == failures) {
                                throw new RuntimeException("fail me!");
                            }
                            Files.write(marker, Integer.toString(counter).getBytes());
                        } else {
                            Files.write(marker, "0".getBytes());
                        }
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }
            }
        """
    }

    void initGit() {
        git = Git.init().setDirectory(testProjectDir.root).call()
        addAll()
        commit("init")
    }

    void stageSuccessfulTest() {
        addAll()
    }

    void commitInNewBranch() {
        git.checkout()
            .setCreateBranch(true)
            .setName("new-branch")
            .call()
        addAll()
        commit("new test")
    }

    RevCommit commit(String message) {
        git.commit()
            .setMessage(message)
            .setAuthor("John", "Doe")
            .call()
    }

    DirCache addAll() {
        git.add().addFilepattern(".").call()
    }


    private AbstractTreeIterator getTreeIterator(String name, Repository repository) throws IOException {
        final ObjectId id = repository.resolve(name)
        if (id == null) {
            throw new IllegalArgumentException(name)
        }
        final CanonicalTreeParser p = new CanonicalTreeParser()
        ObjectReader or = repository.newObjectReader()
        RevWalk walk = new RevWalk(repository)
        p.reset(or, walk.parseTree(id))
        return p
    }
}
