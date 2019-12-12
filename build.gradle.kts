plugins {
    id("nebula.release") version "13.2.1"
}

group = "org.gradle"
description = "Mitigate flaky tests by retrying tests when they fail"

afterEvaluate {
    println("I'm building $name with version $version")
}

evaluationDependsOn("plugin")

val publishPlugins = tasks.findByPath(":plugin:publishPlugins")

tasks.named("final") {
    dependsOn(publishPlugins)
}

tasks.named("candidate") {
    dependsOn(publishPlugins)
}
