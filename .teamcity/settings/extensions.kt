import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.CheckoutMode
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.ParametrizedWithType
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.RelativeId
import jetbrains.buildServer.configs.kotlin.toId

fun BuildType.agentRequirement(os: Os) {
    requirements {
        contains("teamcity.agent.jvm.os.name", os.requirementName)
    }
}

fun BuildType.notEc2Requirement() {
    requirements {
        doesNotContain("teamcity.agent.name", "ec2")
    }
}

fun ParametrizedWithType.java8Home(os: Os) {
    param("env.JDK8", "%${os.name}.java8.oracle.64bit%")
}

fun ParametrizedWithType.java17Home(os: Os) {
    param("env.JDK17", "%${os.name}.java17.openjdk.64bit%")
}

const val useGradleInternalScansServer = "-I gradle/init-scripts/build-scan.init.gradle.kts"

const val buildCacheSetup = "--build-cache -Dgradle.cache.remote.push=true"
/**
 * Creates a new subproject with the given name, automatically deriving the [Project.id] from the name.
 *
 * Using this method also implicitly ensures that subprojects are ordered by creation order.
 */
fun Project.subProject(projectName: String, init: Project.() -> Unit): Project {
    val parent = this
    val subProject = subProject {
        name = projectName
        id = RelativeId(name.toId(stripRootProject(parent.id.toString())))
    }.apply(init)

    this.subProjectsOrder += subProject.id!!

    return subProject
}

fun Project.buildType(buildTypeName: String, init: BuildType.() -> Unit): BuildType {
    val buildType = buildType {
        name = buildTypeName
        id = RelativeId(name.toId(stripRootProject(this@buildType.id.toString())))

        artifactRules = "build/reports/** => reports"
        agentRequirement(Os.linux) // default

        params {
            java8Home(Os.linux)
            java17Home(Os.linux)

            param("env.GRADLE_CACHE_REMOTE_URL", "%gradle.cache.remote.url%")
            param("env.GRADLE_CACHE_REMOTE_USERNAME", "%gradle.cache.remote.username%")
            param("env.GRADLE_CACHE_REMOTE_PASSWORD", "%gradle.cache.remote.password%")
        }

        vcs {
            root(DslContext.settingsRoot)
            checkoutMode = CheckoutMode.ON_AGENT
        }
    }.apply(init)

    this.buildTypesOrderIds += buildType.id!!
    return buildType
}

fun stripRootProject(id: String): String {
    return id.replace("${DslContext.projectId.value}_", "")
}
