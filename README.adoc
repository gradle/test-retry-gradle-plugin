:imagesdir: docs/images
:toc:
:toc-placement!:
:figure-caption!:
:caption!:

= Test Retry Gradle plugin

A Gradle plugin that augments Gradle’s built-in test task with the ability to retry tests that have failed.

image:https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/org/gradle/test-retry/org.gradle.test-retry.gradle.plugin/maven-metadata.xml.svg?label=version["Version",link="https://plugins.gradle.org/plugin/org.gradle.test-retry"]
image:https://img.shields.io/github/license/gradle/test-retry-gradle-plugin["GitHub license",link="https://github.com/gradle/test-retry-gradle-plugin/blob/main/LICENSE"]

toc::[]

== What it does

The plugin causes failed tests to be retried within the same task.
After executing all tests, any failed tests are retried.
The process repeats with tests that continue to fail until the maximum specified number of retries has been attempted,
or there are no more failing tests.

By default, all failed tests passing on retry prevents the test task from failing.
This mode prevents _flaky_ tests from causing build failure.
This setting can be changed so that flaky tests cause build failure, which can be used to identify flaky tests.

When something goes badly wrong and all tests start failing, it can be preferable to not keep retrying tests.
This can happen for example if a disk fills up or a required database is not available.
To avoid this, the plugin can be configured to stop retrying after a certain number of total test failures.

**NOTE:** Retrying tests alone is not a viable flaky test mitigation strategy.
This plugin should only be used alongside processes for tracking and fixing discovered flaky tests.

== Usage

Apply the plugin using one of the two methods described on the https://plugins.gradle.org/plugin/org.gradle.test-retry[Gradle Plugin Portal], where the plugin is listed as `org.gradle.test-retry`. It is compatible with Gradle 5.0 and later versions.

By default, retrying is not enabled.

Retrying is configured per test task via the `retry` extension added to each task by the plugin.

.build.gradle:
[source,groovy]
----
test {
    retry {
        maxRetries = 2
        maxFailures = 20
        failOnPassedAfterRetry = true
    }
}
----


.build.gradle.kts:
[source,kotlin]
----
test {
    retry {
        maxRetries.set(2)
        maxFailures.set(20)
        failOnPassedAfterRetry.set(true)
    }
}
----

=== Limiting retry to CI builds

You may find that local developer builds do not benefit much from retry behaviour, particularly when those tests are invoked via your IDE. In that case we recommend enabling retry only for CI builds.

.build.gradle:
[source,groovy]
----
boolean isCiServer = System.getenv().containsKey("CI")
test {
    retry {
        if (isCiServer) {
            maxRetries = 2
            maxFailures = 20
        }
        failOnPassedAfterRetry = true
    }
}
----

== The `retry` extension

The `retry` extension is of the following type:

[source,java]
----
package org.gradle.testretry;

import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.testing.Test;

/**
 * Allows configuring test retry mechanics.
 * <p>
 * This extension is added with the name 'retry' to all {@link Test} tasks.
 */
public interface TestRetryTaskExtension {

    /**
     * The name of the extension added to each test task.
     */
    String NAME = "retry";

    /**
     * Whether tests that initially fail and then pass on retry should fail the task.
     * <p>
     * This setting defaults to {@code false},
     * which results in the task not failing if all tests pass on retry.
     * <p>
     * This setting has no effect if {@link Test#getIgnoreFailures()} is set to true.
     *
     * @return whether tests that initially fails and then pass on retry should fail the task
     */
    Property<Boolean> getFailOnPassedAfterRetry();

    /**
     * The maximum number of times to retry an individual test.
     * <p>
     * This setting defaults to {@code 0}, which results in no retries.
     * Any value less than 1 disables retrying.
     *
     * @return the maximum number of times to retry an individual test
     */
    Property<Integer> getMaxRetries();

    /**
     * The maximum number of test failures that are allowed before retrying is disabled.
     * <p>
     * The count applies to each round of test execution.
     * For example, if maxFailures is 5 and 4 tests initially fail and then 3 again on retry,
     * this will not be considered too many failures and retrying will continue (if maxRetries {@literal >} 1).
     * If 5 or more tests were to fail initially then no retry would be attempted.
     * <p>
     * This setting defaults to {@code 0}, which results in no limit.
     * Any value less than 1 results in no limit.
     *
     * @return the maximum number of test failures that are allowed before retrying is disabled
     */
    Property<Integer> getMaxFailures();

    /**
     * The filter for specifying which tests may be retried.
     */
    Filter getFilter();

    /**
     * The filter for specifying which tests may be retried.
     */
    void filter(Action<? super Filter> action);

    /**
     * A filter for specifying which tests may be retried.
     *
     * By default, all tests are eligible for retrying.
     */
    interface Filter {

        /**
         * The patterns used to include tests based on their class name.
         *
         * The pattern string matches against qualified class names.
         * It may contain '*' characters, which match zero or more of any character.
         *
         * A class name only has to match one pattern to be included.
         *
         * If no patterns are specified, all classes (that also meet other configured filters) will be included.
         */
        SetProperty<String> getIncludeClasses();

        /**
         * The patterns used to include tests based on their class level annotations.
         *
         * The pattern string matches against the qualified class names of a test class's annotations.
         * It may contain '*' characters, which match zero or more of any character.
         *
         * A class need only have one annotation matching any of the patterns to be included.
         *
         * Annotations present on super classes that are {@code @Inherited} are considered when inspecting subclasses.
         *
         * If no patterns are specified, all classes (that also meet other configured filters) will be included.
         */
        SetProperty<String> getIncludeAnnotationClasses();

        /**
         * The patterns used to exclude tests based on their class name.
         *
         * The pattern string matches against qualified class names.
         * It may contain '*' characters, which match zero or more of any character.
         *
         * A class name only has to match one pattern to be excluded.
         *
         * If no patterns are specified, all classes (that also meet other configured filters) will be included.
         */
        SetProperty<String> getExcludeClasses();

        /**
         * The patterns used to exclude tests based on their class level annotations.
         *
         * The pattern string matches against the qualified class names of a test class's annotations.
         * It may contain '*' characters, which match zero or more of any character.
         *
         * A class need only have one annotation matching any of the patterns to be excluded.
         *
         * Annotations present on super classes that are {@code @Inherited} are considered when inspecting subclasses.
         *
         * If no patterns are specified, all classes (that also meet other configured filters) will be included.
         */
        SetProperty<String> getExcludeAnnotationClasses();

    }

}
----

== Supported test frameworks

Other versions are likely to work as well, but are not tested.

[%header,cols=2*]
|===
|Framework
|Version Tested

|JUnit4
|4.13.2

|JUnit5
|5.8.2

|Spock
|2.0-groovy-3.0

|TestNG
|7.4.0
|===

=== Parameterized tests

In a few cases, test selection for testing frameworks limits the granularity at which tests can be retried.
In each case, this plugin retries at worst at method level.
For JUnit5 `@ParameterizedTest`, TestNG `@Test(dataProvider = "...")`,
and Spock `@Unroll` tests the plugin will retry the entire method with all parameters including those that initially passed.

=== Test dependencies

The plugin supports retrying Spock `@Stepwise` tests and TestNG `@Test(dependsOn = { … })` tests.

* Upstream tests (those that the failed test depends on) are run because a flaky test may depend on state from the prior execution of an upstream test.
* Downstream tests are run because a flaky test causes any downstream tests to be skipped in the initial test run.

=== Custom test frameworks

Some projects may use test tasks with a custom `TestFramework` to execute tests.
If this is the case, the plugin disables retries and emits the following warning:

[source]
----
> Task :unsupportedTestTaskUnitTest
Test retry requested for task :unsupportedTestTaskUnitTest with unsupported test framework CustomTestFramework - failing tests will not be retried
----

To avoid this warning, we can disable retries for the unsupported test task with:

.build.gradle:
[source,groovy]
----
test.named('unsupportedTestTaskUnitTest') {
    retry {
        maxRetries = 0
    }
}
----

.build.gradle.kts:
[source,kotlin]
----
tasks.named<Test>("unsupportedTestTaskUnitTest") {
    retry {
        maxRetries.set(0)
    }
}
----

== Filtering

By default, all tests are eligible for retrying.
The `filter` component of the test retry extension can be used to control which tests should be retried and which should not.

The decision to retry a test or not is based on the tests reported class name, regardless of the name of the test case or method.
The annotations present or not on this class can also be used as the criteria.

.build.gradle:
[source,groovy]
----
test {
    retry {
        maxRetries = 3
        filter {
            // filter by qualified class name (* matches zero or more of any character)
            includeClasses.add("*IntegrationTest")
            excludeClasses.add("*DatabaseTest")

            // filter by class level annotations
            // Note: @Inherited annotations are respected
            includeAnnotationClasses.add("*Retryable")
            excludeAnnotationClasses.add("*NonRetryable")
        }
    }
}
----

== Reporting

=== Gradle

Each execution of a test is discretely reported in Gradle-generated XML and HTML reports.

image:gradle-reports-test-retry-reporting2.png[Gradle test reporting, align="center", title=Gradle HTML test report]

image:gradle-reports-test-retry-reporting.png[Gradle flaky test reporting, align="center", title=Flaky test reported Gradle HTML test report]

Similar to the XML and HTML reports, the console log will also report each individual test execution.
Before retrying a failed test, Gradle will execute the whole test suite of the test task.
This means that all executions of the same test may not be grouped in the console log.

image:gradle-console-test-retry-reporting.png[Gradle console reporting, align="center", title=Flaky test Gradle console output]

=== Gradle Enterprise

Gradle build scans (`--scan` option) report discrete test executions as "Execution [N of total]" and will mark a test with both a _failed_ and then a _passed_ outcome as _flaky_.

image:gradle-build-scan-test-retry-reporting.png[Gradle build scan reporting, align="center", title="Gradle build scan test report", caption="Build scan Tests view"]

Flaky tests can also be visualized across many builds using the https://gradle.com/blog/flaky-tests/[Gradle Enterprise Tests Dashboard].

image:gradle-enterprise-flaky-test-reporting.png[Gradle Enterprise top tests report, align="center", title=Gradle Enterprise top tests report]

=== IDEs

The plugin has been tested with https://www.jetbrains.com/idea[IDEA], https://www.eclipse.org[Eclipse IDE] and https://www.netbeans.org[Netbeans].

==== IDEA

When delegating test execution to Gradle, each execution is reported discretely as for the test reports. Running tests without Gradle delegation causes tests to not be retried.

image:idea-test-retry-reporting.png[IDEA test reporting, align="center", title=IDEA test retry reporting]

==== Eclipse

When delegating test execution to Gradle, each execution is reported discretely as for the test reports. Running tests without Gradle delegation causes tests to not be retried.

image:eclipse-test-retry-reporting.png[Eclipse test reporting, align="center", title=Eclipse test retry reporting]

==== Netbeans

Netbeans only shows the last execution of a test.

image:netbeans-test-retry-reporting.png[Netbeans test reporting, align="center", title=Netbeans test retry reporting]

=== CI tools

The plugin has been tested with the reporting of https://www.jetbrains.com/teamcity[TeamCity] and https://www.jenkins.io[Jenkins].

==== TeamCity

Flaky tests (tests being executed multiple times but with different results) are detected by TeamCity and marked as flaky.
TeamCity lists each test that was executed and how often it was run in the build.

By default, TeamCity will fail your build https://www.jetbrains.com/help/teamcity/build-failure-conditions.html#BuildFailureConditions-Commonbuildfailureconditions[if at least one test fails].
When using `failOnPassedAfterRetry = false` (ie. the default for this plugin), this failure condition should be disabled.

image:teamcity-test-retry-reporting.png[Teamcity test reporting, align="center", title=TeamCity test retry reporting including flaky test detection]

==== Jenkins

Jenkins reports each test execution discretely.

image:jenkins-test-retry-reporting.png[Jenkins test reporting, align="center", title=Jenkins test retry reporting]
