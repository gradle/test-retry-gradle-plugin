/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.net.URI


fun systemProperty(propertyName: String) =
    providers.systemProperty(propertyName).forUseAtConfigurationTime()
/*
 * This script is applied to the settings in buildSrc and the main build. That is why we
 * need this to be a script unless we can model dual usage better with composite/included builds or another solution.
 */
val remoteCacheUrl = systemProperty("gradle.cache.remote.url").map { URI(it) }
val isCiServer = systemProperty("CI").isPresent
val remotePush = systemProperty("gradle.cache.remote.push").map { it != "false" }
val remoteCacheUsername = systemProperty("gradle.cache.remote.username")
val remoteCachePassword = systemProperty("gradle.cache.remote.password")

val isRemoteBuildCacheEnabled =
    remoteCacheUrl.isPresent && gradle.startParameter.isBuildCacheEnabled && !gradle.startParameter.isOffline
val disableLocalCache = systemProperty("disableLocalCache").map { it.toBoolean() }.orElse(false)
if (isRemoteBuildCacheEnabled) {
    buildCache {
        remote(HttpBuildCache::class.java) {
            url = remoteCacheUrl.get()
            isPush = isCiServer && remotePush.get()
            if (remoteCacheUsername.isPresent && remoteCachePassword.isPresent) {
                credentials {
                    username = remoteCacheUsername.get()
                    password = remoteCachePassword.get()
                }
            }
        }
    }
}

if (disableLocalCache.get()) {
    buildCache {
        local {
            isEnabled = false
        }
    }
}
