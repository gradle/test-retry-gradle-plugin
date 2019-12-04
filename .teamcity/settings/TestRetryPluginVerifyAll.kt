import jetbrains.buildServer.configs.kotlin.v2018_2.*

object TestRetryPluginVerifyAll : BuildType({
    name = "Verify all"
    artifactRules = "build/reports/** => reports"

    testRetryVcs()

    dependencies {
        snapshot(RelativeId("GradleTestRetryPlugin_LinuxJava18")) {
            onDependencyFailure = FailureAction.CANCEL
            onDependencyCancel = FailureAction.CANCEL
        }
    }
})
