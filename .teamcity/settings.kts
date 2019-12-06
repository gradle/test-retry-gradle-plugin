import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.schedule
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.vcs

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

version = "2019.1"

project {

    buildType(null_QuickFeedback)
    buildType(null_CrossVersionTestLinuxJava18)
    buildType(null_PublishSnapshot)
    buildType(null_VerifyAll)

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
        text("systemProp.org.gradle.internal.publish.checksums.insecure", "true")
    }
    buildTypesOrder = arrayListOf(null_QuickFeedback, null_CrossVersionTestLinuxJava18, null_VerifyAll, null_PublishSnapshot)
}

object null_CrossVersionTestLinuxJava18 : BuildType({
    name = "CrossVersionTest Linux - Java 1.8"

    artifactRules = "build/reports/** => reports"

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    vcs {
        root(DslContext.settingsRoot)

        checkoutMode = CheckoutMode.ON_SERVER
    }

    steps {
        gradle {
            tasks = "clean testAll"
            buildFile = ""
            gradleParams = "-s -I gradle/init-scripts/build-scan.init.gradle.kts"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
    }

    dependencies {
        snapshot(null_QuickFeedback) {
            onDependencyFailure = FailureAction.CANCEL
            onDependencyCancel = FailureAction.CANCEL
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})

object null_PublishSnapshot : BuildType({
    name = "Publish Snapshot"
    description = "Publish Gradle Test Retry Plugin snapshot to Gradle's Artifactory repository"

    artifactRules = "build/reports/** => reports"

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    vcs {
        root(DslContext.settingsRoot)

        checkoutMode = CheckoutMode.ON_SERVER
    }

    steps {
        gradle {
            tasks = "clean devSnapshot publishPluginMavenPublicationToGradleBuildInternalRepository"
            buildFile = ""
            gradleParams = "-PartifactoryUsername=%ARTIFACTORY_USERNAME% -PartifactoryPassword=%ARTIFACTORY_PASSWORD% -I gradle/init-scripts/build-scan.init.gradle.kts"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
    }

    triggers {
        schedule {
            schedulingPolicy = daily {
                hour = 2
            }
            branchFilter = "+:refs/head/master"
            triggerBuild = always()
            withPendingChangesOnly = false
        }
    }

    dependencies {
        snapshot(null_VerifyAll) {
            onDependencyFailure = FailureAction.CANCEL
            onDependencyCancel = FailureAction.CANCEL
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})

object null_QuickFeedback : BuildType({
    name = "Quick Feedback"

    artifactRules = "build/reports/** => reports"

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    vcs {
        root(DslContext.settingsRoot)

        checkoutMode = CheckoutMode.ON_SERVER
    }

    steps {
        gradle {
            tasks = "clean build"
            buildFile = ""
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})

object null_VerifyAll : BuildType({
    name = "Verify all"

    artifactRules = "build/reports/** => reports"

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    vcs {
        root(DslContext.settingsRoot)

        checkoutMode = CheckoutMode.ON_SERVER
    }

    triggers {
        vcs {
        }
    }

    dependencies {
        snapshot(null_CrossVersionTestLinuxJava18) {
            onDependencyFailure = FailureAction.CANCEL
            onDependencyCancel = FailureAction.CANCEL
        }
        snapshot(null_QuickFeedback) {
            onDependencyFailure = FailureAction.CANCEL
            onDependencyCancel = FailureAction.CANCEL
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
