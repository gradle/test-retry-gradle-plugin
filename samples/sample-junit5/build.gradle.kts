plugins {
    java
    jacoco
    id("org.gradle.test-retry") version "@test-retry-plugin-version@"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    doFirst {
        file("marker.file").delete()
    }

    // to avoid flaky output from Gradle which sometimes reports the task as failed before emitting failed test events
    ignoreFailures = true

    useJUnitPlatform()
    retry {
        maxRetries.set(2)
    }
}
