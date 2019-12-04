import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle

object TestRetryPluginQuickFeedback : BuildType({
    name = "Quick Feedback"
    artifactRules = "build/reports/** => reports"

    testRetryVcs()

    params {
        java8Home(Os.linux)
    }

    steps {
        gradle {
            tasks = "clean build"
            buildFile = ""
        }
    }

    agentRequirement(Os.linux)
})
