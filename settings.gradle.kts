plugins {
    id("com.gradle.enterprise").version("3.6.3")
    id("com.gradle.enterprise.gradle-enterprise-conventions-plugin").version("0.8-alpha1")
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
