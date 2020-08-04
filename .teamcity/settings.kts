import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.project
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.schedule
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2019_2.version

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2019.2"

project {
    params {
        java8Home(Os.linux)
        text("systemProp.org.gradle.internal.publish.checksums.insecure", "true")
    }

    val quickFeedbackBuildType = buildType("Quick Feedback") {
        steps {
            gradle {
                tasks = "clean build"
                buildFile = ""
                gradleParams = "-s $useGradleInternalScansServer $buildCacheSetup"
            }
        }
    }
    val crossVersionTestLinux = buildType("CrossVersionTest Gradle Releases Linux - Java 1.8") {
        steps {
            gradle {
                tasks = "clean testGradleReleases"
                buildFile = ""
                gradleParams = "-s $useGradleInternalScansServer $buildCacheSetup"
                param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
            }
        }

        dependencies {
            snapshot(quickFeedbackBuildType) {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
    }

    val nightliesTestLinux = buildType("CrossVersionTest Gradle Nightlies Linux - Java 1.8") {
        steps {
            gradle {
                tasks = "clean testGradleNightlies"
                buildFile = ""
                gradleParams = "-s $useGradleInternalScansServer $buildCacheSetup"
            }
        }

        dependencies {
            snapshot(quickFeedbackBuildType) {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
    }
    val verifyAllBuildType = buildType("Verify all") {
        triggers.schedule {
            schedulingPolicy = daily {
                hour = 2
            }
            branchFilter = "+:refs/head/master"
            triggerBuild = always()
            withPendingChangesOnly = false
        }

        dependencies {
            snapshot(quickFeedbackBuildType) {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
            snapshot(crossVersionTestLinux) {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
            snapshot(nightliesTestLinux) {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
    }

    subProject("Release") {
        buildType("Publish") {
            description = "Publish Gradle Test Retry Plugin snapshot to Gradle's Artifactory repository"

            steps {
                gradle {
                    tasks = "clean devSnapshot publishPluginPublicationToGradleBuildInternalSnapshotsRepository -x test"
                    gradleParams =
                        "-s $useGradleInternalScansServer $buildCacheSetup -PartifactoryUsername=%artifactoryUsername% -PartifactoryPassword=%artifactoryPassword% $useGradleInternalScansServer"
                    param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
                    buildFile = ""
                }
            }
            dependencies {
                snapshot(verifyAllBuildType) {
                    onDependencyFailure = FailureAction.CANCEL
                    onDependencyCancel = FailureAction.CANCEL
                }
            }

            // publish a nightly snapshot
            triggers.schedule {
                schedulingPolicy = daily {
                    hour = 2
                }
                branchFilter = "+:refs/head/master"
                triggerBuild = always()
                withPendingChangesOnly = false
            }

        }

        buildType("Development") {
            description =
                "Publishes Gradle test retry plugin to development plugin portal (plugins.grdev.net)"
            steps {
                gradle {
                    tasks = "clean devSnapshot :plugin:publishPlugins -x test"
                    buildFile = ""
                    gradleParams =
                        "-s $useGradleInternalScansServer -Dgradle.portal.url=https://plugins.grdev.net -Dgradle.publish.key=%pluginPortalPublishKey% -Dgradle.publish.secret=%pluginPortalPublishSecret% %pluginPortalPublishingFlags%"
                }
            }
            dependencies {
                snapshot(verifyAllBuildType) {
                    onDependencyFailure = FailureAction.CANCEL
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
        }

        buildType("Production") {
            description =
                "Publishes Gradle test retry plugin to production plugin portal (plugins.gradle.org)"
            params {
                select(
                    "releaseScope", "", label = "releaseScope", description = "The scope of the release",
                    display = ParameterDisplay.PROMPT, options = listOf("major", "minor", "patch")
                )
                text(
                    "githubUsername",
                    "",
                    label = "GitHub Username",
                    display = ParameterDisplay.PROMPT,
                    allowEmpty = false
                )
                password("githubToken", "", label = "GitHub Access Token", display = ParameterDisplay.PROMPT)
            }
            steps {
                gradle {
                    tasks = "clean final -x test"
                    buildFile = ""
                    gradleParams =
                        "-s $useGradleInternalScansServer -Prelease.scope=%releaseScope% -Dgradle.publish.key=%pluginPortalPublishKey% -Dgradle.publish.secret=%pluginPortalPublishSecret% -Dorg.ajoberstar.grgit.auth.username=%githubUsername% -Dorg.ajoberstar.grgit.auth.password=%githubToken% %pluginPortalPublishingFlags%"
                }
            }
            dependencies {
                snapshot(verifyAllBuildType) {
                    onDependencyFailure = FailureAction.CANCEL
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
        }

    }


}

