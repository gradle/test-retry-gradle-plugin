package org.gradle.testretry.build

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.gradle.util.GradleVersion

import java.util.regex.Pattern

import static java.util.Map.Entry

@CompileStatic
class GradleVersionData {

    static final Pattern MAJOR_AND_MINOR_VERSION_PATTERN = ~/(?<major>\d+)\.(?<minor>\d+).*/

    static List<String> getLatestNightly() {
        def nightly = new JsonSlurper().parse(new URL("https://services.gradle.org/versions/nightly"))
        [nightly['version'] as String]
    }

    static List<String> getLatestReleaseNightly() {
        def releaseNightly = new JsonSlurper().parse(new URL("https://services.gradle.org/versions/release-nightly"))
        [releaseNightly['version'] as String]
    }

    static List<String> getReleasedVersions(int majorVersion) {
        def GRADLE_5 = GradleVersion.version("5.0")

        new JsonSlurper().parse(new URL("https://services.gradle.org/versions/all"))
            .findAll { !it['nightly'] && !it['snapshot'] } // filter out snapshots and nightlies
            .findAll { !it['rcFor'] || it['activeRc'] } // filter out inactive rcs
            .findAll { !it['milestoneFor'] } // filter out milestones
            .<String, GradleVersion, String> collectEntries { [(it['version']): GradleVersion.version(it['version'] as String)] }
            .findAll { it.value >= GRADLE_5 } // only 5.0 and above
            .findAll { major(it.value) == majorVersion }
            .inject([] as List<Entry<String, GradleVersion>>) { releasesToTest, version -> // only test against latest patch versions
                if (!releasesToTest.any { major(it.value) == major(version.value) && minor(it.value) == minor(version.value) }) {
                    releasesToTest + version
                } else {
                    releasesToTest
                }
            }
            .collect { Entry<String, GradleVersion> it -> it.key.toString() }
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
            } catch (RuntimeException exc) {
                throw new IllegalArgumentException("Failed to determine ${groupName} group for version ${version}", exc)
            }
        } else {
            throw new IllegalArgumentException("Failed to determine ${groupName} group for version ${version}")
        }
    }
}

