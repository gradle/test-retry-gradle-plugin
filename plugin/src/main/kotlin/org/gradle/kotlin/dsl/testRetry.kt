@file:Suppress("unused")

package org.gradle.kotlin.dsl

import org.gradle.api.tasks.testing.Test
import org.gradle.plugins.testretry.RetryTestTaskExtension

val Test.retry: RetryTestTaskExtension
    get() = the()

fun Test.retry(configure: RetryTestTaskExtension.() -> Unit) =
        extensions.configure("retry", configure)
