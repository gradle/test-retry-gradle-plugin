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

import org.gradle.testretry.AbstractFrameworkFuncTest
import spock.lang.Issue
import spock.lang.Unroll

class JUnit4FuncTest extends AbstractFrameworkFuncTest {
    @Override
    String getLanguagePlugin() {
        return 'java'
    }

    @Override
    String getTestAnnotation() {
        return "@org.junit.Test"
    }

    protected isRerunsAllParameterizedIterations() {
        false
    }

    protected String initializationErrorSyntheticTestMethodName(String gradleVersion) {
        "initializationError"
    }

    protected String classRuleAfterErrorTestMethodName(String gradleVersion) {
        "classMethod"
    }

    protected String classRuleBeforeErrorTestMethodName(String gradleVersion) {
        "classMethod"
    }

    @Unroll
    def "handles failure in #lifecycle (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme;

            public class SuccessfulTests {
                @org.junit.${lifecycle}
                public ${lifecycle.contains('Class') ? 'static ' : ''}void lifecycle() {
                    ${flakyAssert()}
                }

                @org.junit.Test
                public void successTest() {}
            }
        """

        when:
        def result = gradleRunner(gradleVersion as String).build()

        then:
        result.output.count('successTest PASSED') >= 1

        where:
        [gradleVersion, lifecycle] << GroovyCollections.combinations((Iterable) [
            GRADLE_VERSIONS_UNDER_TEST,
            ['BeforeClass', 'Before', 'AfterClass', 'After']
        ])
    }

    @Unroll
    def "handles parameterized test in super class (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme;

            import static org.junit.Assert.assertTrue;

            import org.junit.Test;

            abstract class AbstractTest {
                private int input;
                private boolean expected;

                public AbstractTest(int input, boolean expected) {
                    this.input = input;
                    this.expected = expected;
                }

                @Test
                public void test() {
                    assertTrue(expected);
                }
            }
        """

        writeTestSource """
            package acme;

            import java.util.Arrays;
            import java.util.Collection;

            import org.junit.runner.RunWith;
            import org.junit.runners.Parameterized;
            import org.junit.runners.Parameterized.Parameters;

            @RunWith(Parameterized.class)
            public class ParameterTest extends AbstractTest {
                @Parameters(name = "{index}: test({0})={1}")
                public static Iterable<Object[]> data() {
                   return Arrays.asList(new Object[][] {
                         { 0, true }, { 1, false }
                   });
                }

                public ParameterTest(int input, boolean expected) {
                    super(input, expected);
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.output.count('test[0: test(0)=true]') == (isRerunsAllParameterizedIterations() ? 2 : 1)
        result.output.count('test[1: test(1)=false]') == 2

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "can rerun on failure in super class (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme;

            abstract class AbstractTest {
                @org.junit.Test
                public void parent() {
                    ${flakyAssert()}
                }
            }
        """

        writeTestSource """
            package acme;

            public class FlakyTests extends AbstractTest {
                @org.junit.Test
                public void inherited() {
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count('parent FAILED') == 1
        result.output.count('parent PASSED') == 1
        result.output.count('inherited PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "handles flaky runner (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme;

            import org.junit.runner.Runner;
            import org.junit.runners.BlockJUnit4ClassRunner;
            import org.junit.runner.RunWith;
            import org.junit.runner.notification.RunNotifier;

            @RunWith(FlakyTests.MyRunner.class)
            public class FlakyTests {

                public static class MyRunner extends BlockJUnit4ClassRunner {
                    public MyRunner(Class<?> type) throws Exception {
                        super(type);
                    }

                    public void run(RunNotifier notifier) {
                        ${flakyAssert()}
                        super.run(notifier);
                    }
                }

                @org.junit.Test
                public void someTest() {
                }
            }


        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count("FlakyTests > ${initializationErrorSyntheticTestMethodName(gradleVersion)} FAILED") == 1
        result.output.count('FlakyTests > someTest PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "handles flaky static initializer (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme;

            public class FlakyTests {
                static {
                    ${flakyAssert()}
                }

                @org.junit.Test
                public void someTest() {
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count('FlakyTests > someTest FAILED') == 1
        result.output.count('java.lang.ExceptionInInitializerError') == 1
        result.output.count('FlakyTests > someTest PASSED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "handles parameterized tests (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme;

            import static org.junit.Assert.assertTrue;

            import java.util.Arrays;
            import java.util.Collection;

            import org.junit.Test;
            import org.junit.runner.RunWith;
            import org.junit.runners.Parameterized;
            import org.junit.runners.Parameterized.Parameters;

            @RunWith(Parameterized.class)
            public class ParameterTest {
                @Parameters(name = "{index}: test({0})={1}")
                public static Iterable<Object[]> data() {
                   return Arrays.asList(new Object[][] {
                         { 0, true }, { 1, false }
                   });
                }

                private int input;
                private boolean expected;

                public ParameterTest(int input, boolean expected) {
                    this.input = input;
                    this.expected = expected;
                }

                @Test
                public void test() {
                    assertTrue(expected);
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.output.count('test[0: test(0)=true] PASSED') == (isRerunsAllParameterizedIterations() ? 2 : 1)
        result.output.count('test[1: test(1)=false] FAILED') == 2

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    @Issue("https://github.com/gradle/test-retry-gradle-plugin/issues/52")
    def "test that is skipped after failure is considered to be still failing (gradle version #gradleVersion)"() {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme;
            import java.nio.file.*;

            public class FlakyTests {
                @org.junit.Test
                public void flakyAssumeTest() {
                   ${flakyAssert()};
                   org.junit.Assume.assumeFalse(${markerFileExistsCheck()});
                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).buildAndFail()

        then:
        result.output.count('flakyAssumeTest FAILED') == 1
        result.output.count('flakyAssumeTest SKIPPED') == 1

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    @Unroll
    def "handles failure in rule before = #failBefore (gradle version #gradleVersion)"(String gradleVersion, boolean failBefore) {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme;
            import java.nio.file.*;

            public class FlakyTests {

                public static class FailingRule implements org.junit.rules.TestRule {
                 
                    @Override
                    public org.junit.runners.model.Statement apply(org.junit.runners.model.Statement base, org.junit.runner.Description description) {
                        return new org.junit.runners.model.Statement() {
                            @Override
                            public void evaluate() throws Throwable {
                                ${failBefore ? flakyAssert() : ""}
                                try {
                                    base.evaluate();
                                } finally {
                                    ${!failBefore ? flakyAssert() : ""}
                                }
                            }
                        };
                    }
                 
                }

                @org.junit.Rule
                public FailingRule rule = new FailingRule();

                @org.junit.Test
                public void ruleTest() {

                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        result.output.count('ruleTest FAILED') == 1
        result.output.count("ruleTest PASSED") == 1

        where:
        [gradleVersion, failBefore] << GroovyCollections.combinations((Iterable) [
            GRADLE_VERSIONS_UNDER_TEST,
            [true, false]
        ])
    }

    @Unroll
    def "handles failure in class rule before = #failBefore (gradle version #gradleVersion)"(String gradleVersion, boolean failBefore) {
        given:
        buildFile << """
            test.retry.maxRetries = 1
        """

        writeTestSource """
            package acme;
            import java.nio.file.*;

            public class FlakyTests {

                public static class FailingRule implements org.junit.rules.TestRule {
                 
                    @Override
                    public org.junit.runners.model.Statement apply(org.junit.runners.model.Statement base, org.junit.runner.Description description) {
                        return new org.junit.runners.model.Statement() {
                            @Override
                            public void evaluate() throws Throwable {
                                ${failBefore ? flakyAssert() : ""}
                                try {
                                    base.evaluate();
                                } finally {
                                    ${!failBefore ? flakyAssert() : ""}
                                }
                            }
                        };
                    }
                 
                }

                @org.junit.ClassRule
                public static FailingRule rule = new FailingRule();

                @org.junit.Test
                public void ruleTest() {

                }
            }
        """

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        if (failBefore) {
            assert result.output.count("${classRuleBeforeErrorTestMethodName(gradleVersion)} FAILED") == 1
            assert result.output.count("ruleTest PASSED") == 1
        } else {
            assert result.output.count("${classRuleAfterErrorTestMethodName(gradleVersion)} FAILED") == 1
            assert result.output.count("ruleTest PASSED") == 2
        }

        where:
        [gradleVersion, failBefore] << GroovyCollections.combinations((Iterable) [
            GRADLE_VERSIONS_UNDER_TEST,
            [true, false]
        ])
    }


}
