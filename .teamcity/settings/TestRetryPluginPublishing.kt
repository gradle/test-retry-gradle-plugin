import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.*

object TestRetryPluginPublishing : BuildType({
    name = "Publish Snapshot"
    description = "Publish Gradle Test Retry Plugin snapshot to Gradle's Artifactory repository"

    artifactRules = "build/reports/** => reports"

    testRetryVcs()

    params {
        java8Home(Os.linux)
        text("ARTIFACTORY_USERNAME", "bot-build-tool", allowEmpty = true)
        password("ARTIFACTORY_PASSWORD", "credentialsJSON:2b7529cd-77cd-49f4-9416-9461f6ac9018", display = ParameterDisplay.HIDDEN)
    }

    steps {
        gradle {
            tasks = "clean publishPluginMavenPublicationToGradleBuildInternalRepository"
            gradleParams = "-PartifactoryUsername=%ARTIFACTORY_USERNAME% -PartifactoryPassword=%ARTIFACTORY_PASSWORD% $useGradleInternalScansServer"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
            buildFile = ""
        }
    }

    dependencies {
        snapshot(RelativeId("VerifyAll")) {
            onDependencyFailure = FailureAction.CANCEL
            onDependencyCancel = FailureAction.CANCEL
        }
    }

    agentRequirement(Os.linux)
})
