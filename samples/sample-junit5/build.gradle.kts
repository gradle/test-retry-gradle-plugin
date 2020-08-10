plugins {
    java
    jacoco
    id("org.gradle.test-retry") version "@test-retry-plugin-version@"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.6.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.2")
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
