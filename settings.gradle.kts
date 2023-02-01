buildscript {
    dependencies {
        classpath("com.google.code.gson:gson:2.10.1")
    }
}

plugins {
    id("com.gradle.enterprise").version("3.10")
    id("io.github.gradle.gradle-enterprise-conventions-plugin").version("0.7.6")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

gradleEnterprise {
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
