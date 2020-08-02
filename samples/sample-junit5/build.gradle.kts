plugins {
    java
    jacoco
    //TODO this needs to replaced as soon as 1.1.7 is out together with junit5.sample.out#L9 and remove `--warning-mode all` from junit5.sample.confL2
    id("org.gradle.test-retry") version "1.1.6"
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
