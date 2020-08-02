plugins {
    id("groovy")
}

repositories {
    maven { url = uri("https://repo.gradle.org/gradle/libs") }
}

dependencies {
    testImplementation("org.codehaus.groovy:groovy-all:2.5.8")
    testImplementation(gradleTestKit())
    testImplementation("org.gradle:sample-check:0.12.6")
}

val copySamples by tasks.registering(Sync::class) {
    from("../samples")
    into("$buildDir/samples")
}

tasks.test {
    inputs.files(copySamples)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("samples")
}
