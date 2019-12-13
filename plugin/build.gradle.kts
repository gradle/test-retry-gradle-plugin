import java.net.URI
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    groovy
    `java-gradle-plugin`
    `maven-publish`
    checkstyle
    codenarc
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "0.10.1"
    id("com.github.hierynomus.license") version "0.15.0"
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

repositories {
    jcenter()
}

group = "org.gradle"
description = "Mitigate flaky tests by retrying tests when they fail"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val plugin: Configuration by configurations.creating

configurations.getByName("compileOnly").extendsFrom(plugin)

dependencies {
    plugin("org.ow2.asm:asm:7.2")

    testImplementation(gradleTestKit())
    testImplementation("org.codehaus.groovy:groovy-all:2.5.8")
    testImplementation("org.spockframework:spock-core:1.3-groovy-2.5")
    testImplementation("net.sourceforge.nekohtml:nekohtml:1.9.22")

    codenarc("org.codenarc:CodeNarc:1.0")
}

val shadowJar = tasks.named<ShadowJar>("shadowJar")
shadowJar.configure {
    configurations = listOf(plugin)
    dependencies {
        include(dependency("org.ow2.asm:asm"))
    }
    relocate("org.objectweb.asm", "org.gradle.testretry.org.objectweb.asm")
    classifier = ""
}

tasks.getByName("jar").enabled = false
tasks.getByName("jar").dependsOn(shadowJar)

gradlePlugin {
    plugins {
        create("testRetry") {
            id = "org.gradle.test-retry"
            displayName = "Gradle test retry plugin"
            description = project.description
            implementationClass = "org.gradle.plugins.testretry.TestRetryPlugin"
        }
    }
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(plugin)
}

pluginBundle {
    website = "https://github.com/gradle/test-retry-gradle-plugin"
    vcsUrl = "https://github.com/gradle/test-retry-gradle-plugin.git"
    description = project.description
    tags = listOf("test", "flaky")

    mavenCoordinates {
        groupId = "org.gradle"
        artifactId = "test-retry-gradle-plugin"
    }
}

license {
    header = rootProject.file("gradle/licenseHeader.txt")
    exclude("**/*.tokens")
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
        create<MavenPublication>("plugin") {
            artifactId = "test-retry-gradle-plugin"
            artifact(shadowJar.get()) {
                classifier = null
            }
        }
    }
    repositories {
        maven {
            name = "GradleBuildInternalSnapshots"
            url = URI.create("https://repo.gradle.org/gradle/libs-snapshots-local")
            credentials {
                username = project.findProperty("artifactoryUsername") as String?
                password = project.findProperty("artifactoryPassword") as String?
            }
        }
    }
}

tasks.named<Test>("test") {
    systemProperty("org.gradle.test.gradleVersions", gradle.gradleVersion)
}

tasks.register<Test>("testAll") {
    systemProperty("org.gradle.test.gradleVersions", org.gradle.plugins.build.GradleVersionData.getGradleReleases().joinToString("|"))
}
