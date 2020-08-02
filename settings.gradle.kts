plugins {
    id("com.gradle.enterprise") version "3.4"
}

apply(from = "gradle/build-cache-configuration.settings.gradle.kts")

rootProject.name = "test-retry-plugin"

include("plugin")
include("sample-tests")
