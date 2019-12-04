import jetbrains.buildServer.configs.kotlin.v2018_2.vcs.GitVcsRoot

object VersionedSettings_1 : GitVcsRoot({
    id("VersionedSettings")
    name = "Gradle Test Retry Plugin"
    url = "git@github.com:gradle/test-retry-gradle-plugin.git"
    branch = "master"
    authMethod = uploadedKey {
        uploadedKey = "id_rsa_gradlewaregitbot"
    }
})
