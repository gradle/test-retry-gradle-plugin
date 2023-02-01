plugins {
    id("nebula.release") version "15.3.1"
}

buildScan {
    val buildUrl = System.getenv("BUILD_URL") ?: ""
    if (buildUrl.isNotBlank()) {
        link("Build URL", buildUrl)
    }
}

group = "org.gradle"
description = "Mitigate flaky tests by retrying tests when they fail"

evaluationDependsOn("plugin")

val publishPlugins = tasks.findByPath(":plugin:publishPlugins")

tasks.named("releaseCheck") {
    doFirst {
        if (!JavaVersion.current().isJava8) {
            throw GradleException("Plugin releases should use Java 8.")
        }
    }
}

tasks.named("final") {
    dependsOn(publishPlugins)
}

tasks.named("candidate") {
    dependsOn(publishPlugins)
}

tasks.register<Wrapper>("nightlyWrapper") {
    group = "wrapper"
    doFirst {
        val jsonText = java.net.URL("https://services.gradle.org/versions/$label").readText()
        val versionInfo = Gson().fromJson(jsonText, VersionDownloadInfo::class.java)
        distributionUrl = versionInfo.downloadUrl
    }
}
