plugins {
    java
    jacoco
    id("org.gradle.test-retry") version "@test-retry-plugin-version@"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.0-M1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.0-M1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.0-M1")
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
