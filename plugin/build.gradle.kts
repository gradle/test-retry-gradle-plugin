import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.testretry.build.GradleVersionData
import org.gradle.testretry.build.GradleVersionsCommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.google.gson.Gson
import java.net.URL

plugins {
    java
    groovy
    `java-gradle-plugin`
    `maven-publish`
    checkstyle
    codenarc
    `kotlin-dsl`
    signing
    id("com.gradle.plugin-publish") version "1.2.0"
    id("com.github.hierynomus.license") version "0.16.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.gradle"
description = "Mitigate flaky tests by retrying tests when they fail"

val javaToolchainVersion: String? by project
val javaLanguageVersion = javaToolchainVersion?.let { JavaLanguageVersion.of(it) } ?: JavaLanguageVersion.of(8)

java {
    toolchain {
        languageVersion.set(javaLanguageVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    if (javaLanguageVersion >= JavaLanguageVersion.of(9)) {
        options.release.set(8)
    } else {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}

val plugin: Configuration by configurations.creating

configurations.getByName("compileOnly").extendsFrom(plugin)

dependencies {
    val asmVersion = "9.5"
    plugin("org.ow2.asm:asm:${asmVersion}")

    testImplementation(gradleTestKit())
    testImplementation(localGroovy())
    testImplementation("org.spockframework:spock-core:2.3-groovy-3.0")
    testImplementation("org.spockframework:spock-junit4:2.3-groovy-3.0")
    testImplementation("net.sourceforge.nekohtml:nekohtml:1.9.22")
    testImplementation("org.ow2.asm:asm:${asmVersion}")
    testImplementation("org.jetbrains:annotations:24.0.1")

    codenarc("org.codenarc:CodeNarc:3.2.0")
}

val shadowJar = tasks.named<ShadowJar>("shadowJar")
shadowJar.configure {
    configurations = listOf(plugin)
    dependencies {
        include(dependency("org.ow2.asm:asm"))
    }
    relocate("org.objectweb.asm", "org.gradle.testretry.org.objectweb.asm")
    archiveClassifier.set("")
    from(file("../LICENSE")) {
        into("META-INF")
    }
}

tasks.jar {
    enabled = false
    dependsOn(shadowJar)
}

gradlePlugin {
    website.set("https://github.com/gradle/test-retry-gradle-plugin")
    vcsUrl.set("https://github.com/gradle/test-retry-gradle-plugin.git")
    plugins {
        register("testRetry") {
            id = "org.gradle.test-retry"
            displayName = "Gradle test retry plugin"
            description = project.description
            implementationClass = "org.gradle.testretry.TestRetryPlugin"
            tags.addAll("test", "flaky")
        }
    }
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(plugin)
}

license {
    header = rootProject.file("gradle/licenseHeader.txt")
    excludes(listOf("**/*.tokens", "META-INF/LICENSE", "META-INF/NOTICE.txt", "META-INF/licenses/**"))
    mapping(
        mapOf(
            "java" to "SLASHSTAR_STYLE",
            "groovy" to "SLASHSTAR_STYLE",
            "kt" to "SLASHSTAR_STYLE"
        )
    )
    sourceSets = project.sourceSets
    strictCheck = true
}

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            groupId = "org.gradle"
            artifactId = "test-retry-gradle-plugin"
            pom {
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "GradleBuildInternalSnapshots"
            url = uri("https://repo.grdev.net/artifactory/libs-snapshots-local")
            credentials {
                username = project.findProperty("artifactoryUsername") as String?
                password = project.findProperty("artifactoryPassword") as String?
            }
        }
    }
}

configurations {
    configureEach {
        outgoing {
            val removed = artifacts.removeIf { it.classifier.isNullOrEmpty() }
            if (removed) {
                artifact(tasks.shadowJar) {
                    classifier = ""
                }
            }
        }
    }
    // used by plugin-publish plugin
    archives {
        extendsFrom(signatures.get())
    }
}

signing {
    useInMemoryPgpKeys(System.getenv("PGP_SIGNING_KEY"), System.getenv("PGP_SIGNING_KEY_PASSPHRASE"))
    sign(configurations.archives.get())
}

tasks.withType<Sign>().configureEach {
    enabled = System.getenv("CI") != null
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 4
    useJUnitPlatform()
}

tasks.test {
    systemProperty(
        GradleVersionsCommandLineArgumentProvider.PROPERTY_NAME,
        project.findProperty("testedGradleVersion") ?: gradle.gradleVersion
    )
}

tasks.register<Test>("testGradleReleases") {
    jvmArgumentProviders.add(GradleVersionsCommandLineArgumentProvider(GradleVersionData::getReleasedVersions))
}

tasks.register<Test>("testGradleNightlies") {
    jvmArgumentProviders.add(GradleVersionsCommandLineArgumentProvider(GradleVersionData::getNightlyVersions))
}

private data class VersionDownloadInfo(val version: String, val downloadUrl: String)

tasks.register<Wrapper>("nightlyWrapper") {
    group = "wrapper"
    doFirst {
        val jsonText = URL("https://services.gradle.org/versions/nightly").readText()
        val versionInfo = Gson().fromJson(jsonText, VersionDownloadInfo::class.java)
        distributionUrl = versionInfo.downloadUrl
    }
}
