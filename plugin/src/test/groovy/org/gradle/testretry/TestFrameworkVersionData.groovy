package org.gradle.testretry

trait TestFrameworkVersionData {

    String junit4Dependency() {
        "junit:junit:" + System.getProperty("junit4Version")
    }

    String jupiterDependency() {
        "org.junit.jupiter:junit-jupiter:" + System.getProperty("junit5Version")
    }

    String jupiterApiDependency() {
        "org.junit.jupiter:junit-jupiter-api:" + System.getProperty("junit5Version")
    }

    String jupiterEngineDependency() {
        "org.junit.jupiter:junit-jupiter-engine:" + System.getProperty("junit5Version")
    }

    String jupiterParamsDependency() {
        "org.junit.jupiter:junit-jupiter-params:" + System.getProperty("junit5Version")
    }

    String junitVintageEngineDependency() {
        "org.junit.vintage:junit-vintage-engine:" + System.getProperty("junit5Version")
    }

    String junitPlatformLauncherDependency() {
        "org.junit.platform:junit-platform-launcher:" + System.getProperty("junitPlatformLauncherVersion")
    }

    String mockitoDependency() {
        "org.mockito:mockito-core:" + System.getProperty("mockitoVersion")
    }

    String spock1Dependency() {
        "org.spockframework:spock-core:" + System.getProperty("spock1Version")
    }

    String spock2Dependency() {
        "org.spockframework:spock-core:" + System.getProperty("spock2Version")
    }

    String testNgDependency() {
        "org.testng:testng:" + System.getProperty("testNgVersion")
    }
}
