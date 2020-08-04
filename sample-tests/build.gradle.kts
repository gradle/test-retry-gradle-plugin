import org.gradle.testretry.build.PluginsVersionData

plugins {
    id("groovy")
}

repositories {
    maven { url = uri("https://repo.gradle.org/gradle/libs") }
}

dependencies {
    testImplementation(localGroovy())
    testImplementation(gradleTestKit())
    testImplementation("org.gradle:sample-check:0.12.6")
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
        from(snippetsDir)
        into(processedSnippetsDir)
        inputs.properties(tokens)
        doFirst {
            filter(org.apache.tools.ant.filters.ReplaceTokens::class, "tokens" to tokens.mapValues { it.value.get() })
        }
    }
    test {
        val fileProvider = replaceTokensInSnippets.map { it.destinationDir }
        inputs.files(fileProvider)
            .withPathSensitivity(PathSensitivity.RELATIVE)
            .withPropertyName("samples")
    }
}
