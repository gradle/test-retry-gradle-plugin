import jetbrains.buildServer.configs.kotlin.v2018_2.*

import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.ScheduleTrigger
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.VcsTrigger
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.schedule
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.vcs

object TestRetryPluginVerifyAll : BuildType({
    name = "Verify all"
    artifactRules = "build/reports/** => reports"
    testRetryVcs()
    triggers.vcs {
    }
    dependencies {
        snapshot(RelativeId("LinuxJava18")) {
            onDependencyFailure = FailureAction.CANCEL
            onDependencyCancel = FailureAction.CANCEL
        }
    }
})
