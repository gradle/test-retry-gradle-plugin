package org.gradle.plugins.build

class GradleVersionData {

    static List<String> getGradleReleases() {
        def allReleases = new groovy.json.JsonSlurper().parse(new URL("https://services.gradle.org/versions/all"))
        return allReleases.findAll {!it.nigthly && !it.snapshot }       // filter out snapshots and nightlies
            .findAll{!it.rcFor || it.activeRc}                          // filter out inactive rcs
            .inject([]) {releasesToTest, currentEntry ->   // filter out obsolete milestones
                if(currentEntry.milestoneFor && (releasesToTest.find{processedEntry -> processedEntry.milestoneFor == currentEntry.milestoneFor || processedEntry.version == currentEntry.milestoneFor})) {
                    return releasesToTest
                }
                return releasesToTest + currentEntry
            }
            .collect{it.version}                                       // we're only interested in the verison
            .findAll {((it.substring(0, 1)) as int) >=5 }              // only 5.0 and above
            .inject([]) {releasesToTest, currentEntry ->  // only test against latest patch versions
                if(!releasesToTest.any{it.startsWith(currentEntry.substring(0, 3))}) {
                    return releasesToTest + currentEntry
                } else {
                    return releasesToTest
                }
            }
    }

}
