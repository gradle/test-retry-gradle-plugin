import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildTypeSettings.Type.COMPOSITE
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay.NORMAL
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay.PROMPT
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

version = "2021.1"

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
            branchFilter = "+:refs/head/main"
            triggerBuild = always()
            withPendingChangesOnly = false
        }

        type = COMPOSITE

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
    val projectTriggerRules = """
        -:docs/**
        -:samples/**
        -:.teamcity/**
        -:.github/**
    """.trimIndent()
    val triggerPropertyName = "build.trigger.type"

    val gradleNightlyDogfoodingBuildType = buildType("Gradle Nightly dogfooding (nightly)") {
        steps {
            gradle {
                tasks = "clean nightlyWrapper assemble"
                buildFile = ""
                gradleParams = "--daemon" //-s $useGradleInternalScansServer $buildCacheSetup"
            }
        }
        triggers.schedule {
            triggerRules = projectTriggerRules
            schedulingPolicy = daily {
                hour = 15
                minute = 57
            }
//            branchFilter = "+:<default>"
            branchFilter = "+:refs/head/jgauthier/20027"
            buildParams {
                this.add(Parameter(triggerPropertyName, "SCHEDULED-TRIGGER"))
                this.add(Parameter(triggerPropertyName, "NIGHTLY-TRIGGER"))
            }
            withPendingChangesOnly = false
            triggerBuild = always()
        }
    }

    subProject("Release") {
        buildType("Publish") {
            description = "Publish Gradle Test Retry Plugin snapshot to Gradle's Artifactory repository"

            params {
                param("env.ORG_GRADLE_PROJECT_artifactoryUsername", "%artifactoryUsername%")
                param("env.ORG_GRADLE_PROJECT_artifactoryPassword", "%artifactoryPassword%")
            }

            steps {
                gradle {
                    tasks = "clean devSnapshot publishPluginPublicationToGradleBuildInternalSnapshotsRepository -x test"
                    gradleParams =
                        "-s $useGradleInternalScansServer $buildCacheSetup $useGradleInternalScansServer"
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
                branchFilter = "+:refs/head/main"
                triggerBuild = always()
                withPendingChangesOnly = false
            }

            notEc2Requirement()
        }

        buildType("Development") {
            description =
                "Publishes Gradle test retry plugin to development plugin portal (plugins.grdev.net)"
            params {
                param("env.GRADLE_PUBLISH_KEY", "%development.plugin.portal.publish.key%")
                password("env.GRADLE_PUBLISH_SECRET", "%development.plugin.portal.publish.secret%", display = NORMAL)
            }
            steps {
                gradle {
                    tasks = "clean devSnapshot :plugin:publishPlugins -x test"
                    buildFile = ""
                    gradleParams =
                        "-s $useGradleInternalScansServer -Dgradle.portal.url=https://plugins.grdev.net %pluginPortalPublishingFlags%"
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
                    display = PROMPT, options = listOf("major", "minor", "patch")
                )
                password("env.GRGIT_USER", "", label = "GitHub Access Token", display = PROMPT)
                param("env.GRADLE_PUBLISH_KEY", "%plugin.portal.publish.key%")
                password("env.GRADLE_PUBLISH_SECRET", "%plugin.portal.publish.secret%", display = NORMAL)
            }
            steps {
                gradle {
                    tasks = "clean final -x test"
                    buildFile = ""
                    gradleParams =
                        "-s $useGradleInternalScansServer -Prelease.scope=%releaseScope% %pluginPortalPublishingFlags%"
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
