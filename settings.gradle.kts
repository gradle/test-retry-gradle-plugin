buildscript {
    dependencies {
        classpath("com.google.code.gson:gson:2.11.0")
    }
}

plugins {
    id("com.gradle.develocity").version("3.17.5")
    id("io.github.gradle.gradle-enterprise-conventions-plugin").version("0.10.1")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

develocity {
    buildScan {
        val buildUrl = System.getenv("BUILD_URL") ?: ""
        if (buildUrl.isNotBlank()) {
            link("Build URL", buildUrl)
        }
    }
}

rootProject.name = "test-retry-plugin"

include("plugin")
include("sample-tests")
