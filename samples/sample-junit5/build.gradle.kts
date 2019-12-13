plugins {
    java
    jacoco
    id("org.gradle.test-retry") version "0.2.0"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.5.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
}

tasks.test {
    doFirst {
        file("marker.file").delete()
    }

    useJUnitPlatform()
    retry {
        maxRetries.set(2)
    }
}