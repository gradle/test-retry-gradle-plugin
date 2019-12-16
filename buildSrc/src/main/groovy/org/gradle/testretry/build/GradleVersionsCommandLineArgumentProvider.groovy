package org.gradle.testretry.build

import groovy.transform.CompileStatic
import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider

import java.util.function.Supplier

@CompileStatic
class GradleVersionsCommandLineArgumentProvider implements CommandLineArgumentProvider {

    public static final String PROPERTY_NAME = "org.gradle.test.gradleVersions"

    final Supplier<List<String>> versions

    GradleVersionsCommandLineArgumentProvider(Supplier<List<String>> versions) {
        this.versions = { versions.get() }.memoize() as Supplier<List<String>>
    }

    @Input
    List<String> getVersions() {
        versions.get()
    }

    @Override
    Iterable<String> asArguments() {
        ["-D${PROPERTY_NAME}=${getVersions().join("|")}".toString()]
    }

}
