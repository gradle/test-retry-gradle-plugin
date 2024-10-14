plugins {
    java
    jacoco
    id("org.gradle.test-retry") version "@test-retry-plugin-version@"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.2")
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
