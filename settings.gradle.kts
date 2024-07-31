buildscript {
    dependencies {
        classpath("com.google.code.gson:gson:2.11.0")
    }
}

plugins {
    id("com.gradle.develocity").version("3.17.6")
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.0.2"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

val isCI = providers.environmentVariable("CI").isPresent

develocity {
    server = "https://ge.gradle.org"
    buildScan {
        uploadInBackground = !isCI
        publishing.onlyIf { it.isAuthenticated }
        obfuscation {
            ipAddresses { addresses -> addresses.map { "0.0.0.0" } }
        }
    }
}

buildCache {
    local {
        isEnabled = true
    }

    remote(develocity.buildCache) {
        server = "https://eu-build-cache.gradle.org"
        isEnabled = true
        val accessKey = System.getenv("DEVELOCITY_ACCESS_KEY")
        isPush = isCI && !accessKey.isNullOrEmpty()
    }
}

rootProject.name = "test-retry-plugin"

include("plugin")
include("sample-tests")
