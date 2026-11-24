// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class DirectAppWidgetManagerInstanceUsageTest {
    private val context =
        kotlin(
            """
            package android.content
            class Context
            """.trimIndent(),
        )
    private val manager =
        kotlin(
            """
            package android.appwidget
            import android.content.Context
            object AppWidgetManager {
                fun getInstance(context: Context): AppWidgetManager? = null
            }
            """.trimIndent(),
        )

    @Test
    fun `reports Kotlin calls including aliased imports`() {
        lint()
            .allowMissingSdk()
            .files(
                context,
                manager,
                kotlin(
                    """
                    package test
                    import android.appwidget.AppWidgetManager
                    import android.appwidget.AppWidgetManager as Manager
                    import android.content.Context

                    fun update(context: Context) {
                        AppWidgetManager.getInstance(context)
                        Manager.getInstance(context)
                    }
                    """.trimIndent(),
                ),
            ).issues(DirectAppWidgetManagerInstanceUsage.ISSUE)
            .run()
            .expectErrorCount(2)
            .expectContains("Use getAppWidgetManager(context) and handle its nullable result")
    }

    @Test
    fun `allows suppressed helper and calls to the helper`() {
        lint()
            .allowMissingSdk()
            .files(
                context,
                manager,
                kotlin(
                    """
                    package android.annotation
                    annotation class SuppressLint(vararg val value: String)
                    """.trimIndent(),
                ),
                kotlin(
                    """
                    package com.ichi2.widget
                    import android.annotation.SuppressLint
                    import android.appwidget.AppWidgetManager
                    import android.content.Context

                    @SuppressLint("DirectAppWidgetManagerInstanceUsage")
                    fun getAppWidgetManager(context: Context): AppWidgetManager? =
                        AppWidgetManager.getInstance(context)

                    fun update(context: Context) {
                        val manager = getAppWidgetManager(context) ?: return
                    }
                    """.trimIndent(),
                ),
            ).issues(DirectAppWidgetManagerInstanceUsage.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `allows platform access in test sources`() {
        lint()
            .allowMissingSdk()
            .files(
                context,
                manager,
                kotlin(
                    """
                    package test
                    import android.appwidget.AppWidgetManager
                    import android.content.Context

                    fun inspect(context: Context) = AppWidgetManager.getInstance(context)
                    """.trimIndent(),
                ).to("test/WidgetTest.kt").within("test"),
            ).issues(DirectAppWidgetManagerInstanceUsage.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `ignores unrelated getInstance methods`() {
        lint()
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    package test
                    object OtherManager {
                        fun getInstance() = OtherManager
                    }
                    fun update() {
                        OtherManager.getInstance()
                    }
                    """.trimIndent(),
                ),
            ).issues(DirectAppWidgetManagerInstanceUsage.ISSUE)
            .run()
            .expectClean()
    }
}
