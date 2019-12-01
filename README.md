# Test Retry Gradle Plugin

A standalone (i.e. not part of Gradle Enterprise) Gradle plugin that augments Gradle’s built-in Test task with the ability to retry tests that have failed for the purpose of mitigating test flakiness.

## Developing

Release by running `./gradlew final` which will automatically select the next minor release version, tag the repository, publish the binary to Bintray, and publish the plugin to the Gradle plugin portal. To perform a major version release, `./gradlew final -Prelease.scope=major`. To release a patch, `./gradlew final -Prelease.scope=patch`.

When adding new source files, run `./gradlew lF` to automatically add license headers.