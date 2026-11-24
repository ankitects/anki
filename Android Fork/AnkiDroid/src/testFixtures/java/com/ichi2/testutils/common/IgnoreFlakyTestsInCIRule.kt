// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.common

import com.ichi2.anki.BuildConfig
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.Locale

/**
 * An annotation which marks a test as flaky so it will be skipped if run under CI
 *
 * The test class or a subclass must contain the code:
 *
 * ```kotlin
 *     @get:Rule
 *     val ignoreFlakyTests = IgnoreFlakyTestsInCIRule()
 * ```
 *
 * @see IgnoreFlakyTestsInCIRule
 *
 * @param os The OS The test fails under (required)
 * @param message The message to display when the test is skipped
 */
annotation class Flaky(
    val os: OS,
    val message: String = "",
)

/**
 * Ignores any matching test defined with `@Flaky` in CI
 * @see Flaky
 */
class IgnoreFlakyTestsInCIRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement {
        if (!isRunningUnderCI) return base
        val annotation = description.getFlakyAnnotation() ?: return base
        if (!annotation.os.isRunning()) return base
        return object : Statement() {
            override fun evaluate() {
                val message = if (annotation.message.isEmpty()) "Flaky test" else "Flaky test: " + annotation.message
                Assume.assumeTrue(message, false)
            }
        }
    }

    /**
     * Returns an instance of [Flaky] for the test if annotated,
     * preferring the method-level annotation over the class-level annotation
     */
    private fun Description.getFlakyAnnotation(): Flaky? =
        getAnnotation(Flaky::class.java) ?: this.testClass.getAnnotation(Flaky::class.java)

    companion object {
        val isRunningUnderCI: Boolean = BuildConfig.CI
    }
}

enum class OS {
    WINDOWS,
    MACOS,
    LINUX,
    ALL,
    ;

    fun isRunning(): Boolean = this == ALL || this == currentOS

    companion object {
        val currentOS: OS by lazy {
            val osName = System.getProperty("os.name")?.lowercase(Locale.ENGLISH) ?: throw IllegalStateException("Could not get OS name")
            // values obtained by throwing an exception containing 'System.getProperty("os.name")'
            with(osName) {
                return@lazy when {
                    startsWith("linux") -> LINUX // Linux
                    startsWith("win") -> WINDOWS // Windows Server 2022
                    startsWith("mac") -> MACOS // Mac OS X
                    else -> throw IllegalStateException("Unknown OS: $osName")
                }
            }
        }
    }
}

class IgnoreFlakyTestsTest {
    @get:Rule
    val ignoreFlakyTests = IgnoreFlakyTestsInCIRule()

    @Test
    @Flaky(os = OS.ALL)
    fun ensureFlakyTestsAreOnlyRunLocally() {
        assertThat("Not running under CI", BuildConfig.CI, not(equalTo("true")))
    }
}
