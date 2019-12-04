/*
 * This is an init script for internal usage at Gradle Inc.
 */
val tasksWithBuildScansOnFailure = listOf("verifyTestFilesCleanup", "killExistingProcessesStartedByGradle", "tagBuild").map { listOf(it) }

if (!gradle.startParameter.systemPropertiesArgs.containsKey("disableScanPlugin")) {
    // Gradle 6
    settingsEvaluated {
        pluginManager.withPlugin("com.gradle.enterprise") {
            extensions["gradleEnterprise"].withGroovyBuilder {
                configureExtension(getProperty("buildScan"))
            }
        }
    }
}

fun configureExtension(extension: Any) {
    extension.withGroovyBuilder {
        "publishAlways"()
        setProperty("server", "https://e.grdev.net")

        if (!System.getProperty("slow.internet.connection", "false").toBoolean()) {
            setProperty("captureTaskInputFiles", true)
        }
    }
}
