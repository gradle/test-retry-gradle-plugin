import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.testretry.build.GradleVersionData
import org.gradle.testretry.build.GradleVersionsCommandLineArgumentProvider

plugins {
    java
    groovy
    `java-gradle-plugin`
    `maven-publish`
    checkstyle
    codenarc
    `kotlin-dsl`
    signing
    id("com.gradle.plugin-publish") version "0.13.0"
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
    plugin("org.ow2.asm:asm:9.0")
    plugin("org.eclipse.jgit:org.eclipse.jgit:5.11.1.202105131744-r")

    testImplementation(gradleTestKit())
    testImplementation(localGroovy())
    testImplementation("org.spockframework:spock-core:1.3-groovy-2.5")
    testImplementation("net.sourceforge.nekohtml:nekohtml:1.9.22")
    testImplementation("org.ow2.asm:asm:8.0.1")
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:5.11.1.202105131744-r")

    codenarc("org.codenarc:CodeNarc:1.0")
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

tasks.getByName("jar").enabled = false
tasks.getByName("jar").dependsOn(shadowJar)

gradlePlugin {
    plugins {
        create("testRetry") {
            id = "org.gradle.test-retry"
            displayName = "Gradle test retry plugin"
            description = project.description
            implementationClass = "org.gradle.testretry.TestRetryPlugin"
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
            url = uri("https://repo.gradle.org/gradle/libs-snapshots-local")
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
