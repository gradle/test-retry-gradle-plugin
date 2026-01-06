import com.google.gson.Gson
import org.gradle.testretry.build.GradleVersionData
import org.gradle.testretry.build.GradleVersionsCommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
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
    id("com.gradle.plugin-publish") version "2.0.0"
    id("com.github.hierynomus.license") version "0.16.1"
    id("com.gradleup.shadow") version "9.3.1"
}

group = "org.gradle"
description = "Mitigate flaky tests by retrying tests when they fail"

val javaToolchainVersion: String? by project
val javaLanguageVersion = javaToolchainVersion?.let { JavaLanguageVersion.of(it) } ?: JavaLanguageVersion.of(17)
val isGradle9OrNewer = GradleVersion.current() >= GradleVersion.version("9.0.0")

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
    compilerOptions.jvmTarget = JVM_1_8
}

val plugin: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

configurations.compileOnly {
    extendsFrom(plugin)
}

dependencies {
    plugin(libs.asm)

    testImplementation(gradleTestKit())
    testImplementation(localGroovy())
    testImplementation(platform(libs.spock.groovy3.bom))
    testImplementation(libs.spock.core)
    testImplementation(libs.spock.junit4)
    testImplementation(libs.nekohtml)
    testImplementation(libs.asm)
    testImplementation(libs.jetbrains.annotations)

    testRuntimeOnly(libs.junit.platform.launcher)

    codenarc(libs.codenarc)
}

// The following block makes sure that the produced module descriptor
// (located in `build/publications/pluginMaven/module.json`) has the proper
// attribute `"org.gradle.jvm.version": 8`.
configurations.named(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME).configure {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
}

tasks.shadowJar {
    configurations = listOf(plugin)
    dependencies {
        include(dependency("org.ow2.asm:asm"))
    }
    relocate("org.objectweb.asm", "org.gradle.testretry.org.objectweb.asm")
    archiveClassifier.set("")
    into(".") {
        from(rootProject.layout.projectDirectory.file("LICENSE"))
    }
}

tasks.jar {
    enabled = false
    dependsOn(tasks.shadowJar)
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
    excludes(listOf("**/*.tokens", "LICENSE", "NOTICE.txt", "licenses/**"))
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

signing {
    useInMemoryPgpKeys(System.getenv("PGP_SIGNING_KEY"), System.getenv("PGP_SIGNING_KEY_PASSPHRASE"))
}

tasks.withType<Sign>().configureEach {
    enabled = System.getenv("CI") != null
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 4
    useJUnitPlatform()
}

tasks.withType<Test> {
    systemProperty(
        GradleVersionsCommandLineArgumentProvider.PROPERTY_NAME,
        project.findProperty("testedGradleVersion") ?: gradle.gradleVersion
    )
    systemProperty("junit4Version", libs.versions.junit4.get())
    systemProperty("junit5Version", libs.versions.junit5Jupiter.get())
    systemProperty("junitPlatformLauncherVersion", libs.versions.junitPlatformLauncher.get())
    systemProperty("mockitoVersion", libs.versions.mockito.get())
    systemProperty("spock1Version", libs.versions.spock1.get())
    systemProperty("spock2Version", if (isGradle9OrNewer) libs.versions.spock2.groovy4.get() else libs.versions.spock2.groovy3.get())
    systemProperty("testNgVersion", libs.versions.testNg.get())

    if (project.hasProperty("testJavaToolchainVersion")) {
        val testJavaToolchainVersion = project.property("testJavaToolchainVersion").toString()
        systemProperty("testJavaToolchainVersion", testJavaToolchainVersion)
    }
}

listOf(5, 6, 7, 8, 9).map { gradleMajorVersion ->
    tasks.register<Test>("testGradle${gradleMajorVersion}Releases") {
        jvmArgumentProviders.add(GradleVersionsCommandLineArgumentProvider {
            GradleVersionData.getReleasedVersions(
                gradleMajorVersion
            )
        })
    }
}

tasks.register<Test>("testGradleReleaseNightlies") {
    jvmArgumentProviders.add(GradleVersionsCommandLineArgumentProvider(GradleVersionData::getLatestReleaseNightly))
}

tasks.register<Test>("testGradleNightlies") {
    jvmArgumentProviders.add(GradleVersionsCommandLineArgumentProvider(GradleVersionData::getLatestNightly))
}

private data class VersionDownloadInfo(val version: String, val downloadUrl: String)

tasks.register<Wrapper>("nightlyWrapper") {
    group = "wrapper"
    validateDistributionUrl = true
    doFirst {
        val jsonText = URL("https://services.gradle.org/versions/nightly").readText()
        val versionInfo = Gson().fromJson(jsonText, VersionDownloadInfo::class.java)
        distributionUrl = versionInfo.downloadUrl
    }
}
