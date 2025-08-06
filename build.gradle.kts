import java.io.BufferedReader
import java.io.InputStreamReader

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

fun runCommand(vararg command: String): String {
    val process = ProcessBuilder(command.toList()).start()
    val reader = BufferedReader(InputStreamReader(process.inputStream))
    val output = StringBuilder()
    var line: String?
    while (reader.readLine().also { line = it } != null) {
        output.append(line).append("\n")
    }
    process.waitFor()
    return output.toString()
}

val jar = tasks.getByPath(":plugin:shadowJar")
tasks.named("releaseCheck") {
    inputs.files(jar.outputs.files)
    doFirst {
        val jarPath = jar.outputs.files.singleFile.path
        val classFileInfo = runCommand("javap", "-cp", jarPath, "-v", "org.gradle.testretry.TestRetryPlugin")
        val classFileVersion = requireNotNull("major version: (\\d+)".toRegex().find(classFileInfo)).groupValues.last().toInt()
        val javaVersionForCompilation = JavaVersion.forClassVersion(classFileVersion)
        if (!javaVersionForCompilation.isJava8) {
            throw GradleException("Plugin releases should use Java 8, but was $javaVersionForCompilation")
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
