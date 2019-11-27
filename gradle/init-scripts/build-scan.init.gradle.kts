/*
 * This is an init script for internal usage at Gradle Inc.
 */
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
        if (System.getenv("CI") !in listOf(null, "false")) {
            "tag"("CI")
        }

        if (!System.getProperty("slow.internet.connection", "false").toBoolean()) {
            setProperty("captureTaskInputFiles", true)
        }
    }
}
