plugins {
    id("nebula.release") version "19.0.10"
    id("org.gradle.wrapper-upgrade") version "0.12"
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

wrapperUpgrade {
    gradle {
        register("self") {
            repo.set("gradle/test-retry-gradle-plugin")
            options.gitCommitExtraArgs.add("--signoff")
        }
    }
}
