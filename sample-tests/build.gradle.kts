import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.testretry.build.PluginsVersionData

plugins {
    id("groovy")
}

dependencies {
    testImplementation(localGroovy())
    testImplementation(gradleTestKit())
    testImplementation("org.gradle.exemplar:samples-check:1.0.2")
    testImplementation("junit:junit:4.13.2")
}

val snippetsDir = file("../samples")
val processedSnippetsDir = file("$buildDir/samples")

val tokens: Map<String, Provider<String>> = mapOf(
    "test-retry-plugin-version" to provider {
        PluginsVersionData.latestVersion("org.gradle", "test-retry-gradle-plugin")
    }
)


tasks {
    val replaceTokensInSnippets by registering(Copy::class) {
        into(processedSnippetsDir)
        inputs.properties(tokens)
        from(snippetsDir) {
            // First copy all non-filtered files to preserve binaries such as the gradle-wrapper.jar
            exclude("**/*.gradle.kts")
        }
        from(snippetsDir) {
            include("**/*.gradle.kts")
            doFirst {
                filter(ReplaceTokens::class, "tokens" to tokens.mapValues { it.value.get() })
            }
        }
    }
    test {
        val fileProvider = replaceTokensInSnippets.map { it.destinationDir }
        inputs.files(fileProvider)
            .withPathSensitivity(PathSensitivity.RELATIVE)
            .withPropertyName("samples")
    }
}
