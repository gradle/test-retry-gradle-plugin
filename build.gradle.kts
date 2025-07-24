plugins {
    id("nebula.release") version "20.2.0"
    id("org.gradle.wrapper-upgrade") version "0.12"
}

develocity.buildScan {
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
            throw GradleException("Plugin releases should use Java 8, but used ${JavaVersion.current()} instead.")
        }
    }
}

tasks.named("final") {
    publishPlugins?.let { dependsOn(it) }
}

tasks.named("candidate") {
    publishPlugins?.let { dependsOn(it) }
}

wrapperUpgrade {
    gradle {
        register("self") {
            repo.set("gradle/test-retry-gradle-plugin")
            options.gitCommitExtraArgs.add("--signoff")
        }
    }
}
