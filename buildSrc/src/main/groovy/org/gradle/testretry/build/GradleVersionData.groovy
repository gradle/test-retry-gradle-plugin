package org.gradle.testretry.build

import groovy.json.JsonSlurper
import org.gradle.util.GradleVersion

import java.util.regex.Pattern

class GradleVersionData {

    static final Pattern MAJOR_AND_MINOR_VERSION_PATTERN = ~/(?<major>\d+)\.(?<minor>\d+).*/

    static List<String> getNightlyVersions() {
        def releaseNightly = getLatestReleaseNightly()
        releaseNightly ? [releaseNightly] + getLatestNightly() : [getLatestNightly()]
    }

    private static String getLatestNightly() {
        new JsonSlurper().parse(new URL("https://services.gradle.org/versions/nightly")).version
    }

    private static String getLatestReleaseNightly() {
        new JsonSlurper().parse(new URL("https://services.gradle.org/versions/release-nightly")).version
    }

    static List<String> getReleasedVersions() {
        def GRADLE_5 = GradleVersion.version("5.0")

        new JsonSlurper().parse(new URL("https://services.gradle.org/versions/all"))
            .findAll { !it.nightly && !it.snapshot } // filter out snapshots and nightlies
            .findAll { !it.rcFor || it.activeRc } // filter out inactive rcs
            .findAll { !it.milestoneFor } // filter out milestones
            .<String, GradleVersion, String>collectEntries { [(it.version): GradleVersion.version(it.version as String)] }
            .findAll { it.value >= GRADLE_5 } // only 5.0 and above
            .inject([] as List<Map.Entry<String, GradleVersion>>) { releasesToTest, version -> // only test against latest patch versions
                if (!releasesToTest.any { major(it.value) == major(version.value) && minor(it.value) == minor(version.value) }) {
                    releasesToTest + version
                } else {
                    releasesToTest
                }
            }
            .collect { it.key.toString() }
    }

    static int major(GradleVersion gradle) {
        extractedGroup(gradle, "major")
    }

    static int minor(GradleVersion gradle) {
        extractedGroup(gradle, "minor")
    }

    static int extractedGroup(GradleVersion gradle, String groupName) {
        def version = gradle.version
        def matcher = MAJOR_AND_MINOR_VERSION_PATTERN.matcher(version)
        if (matcher.matches()) {
            try {
                return Integer.parseInt(matcher.group(groupName))
            } catch(RuntimeException exc) {
                throw new IllegalArgumentException("Failed to determine ${groupName} group for version ${version}", exc)
            }
        } else {
            throw new IllegalArgumentException("Failed to determine ${groupName} group for version ${version}")
        }
    }

}

