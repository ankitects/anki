// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.java
import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class IntentActionNamingDetectorTest {
    @Test
    fun `reports constants in intent and filter constructors and methods`() {
        lint()
            .allowMissingSdk()
            .files(
                intent,
                intentFilter,
                context,
                kotlin(
                    """
                    package test
                    import android.content.Context
                    import android.content.Intent
                    import android.content.IntentFilter

                    const val UPDATE_WIDGET = "update"
                    const val MIME_TYPE = "text/plain"
                    fun send(context: Context, intent: Intent) {
                        Intent(UPDATE_WIDGET)
                        Intent(UPDATE_WIDGET, null)
                        Intent(UPDATE_WIDGET, null, context, Context::class.java)
                        IntentFilter(UPDATE_WIDGET)
                        IntentFilter(UPDATE_WIDGET, MIME_TYPE)
                        IntentFilter.create(UPDATE_WIDGET, MIME_TYPE)
                        intent.setAction(UPDATE_WIDGET)
                        intent.action = UPDATE_WIDGET
                        Intent().apply { action = UPDATE_WIDGET }
                        IntentFilter().addAction(UPDATE_WIDGET)
                        IntentFilter().apply { addAction(UPDATE_WIDGET) }
                    }
                    """,
                ).indented(),
            ).issues(IntentActionNamingDetector.ISSUE)
            .run()
            .expectErrorCount(11)
            .expectContains("Intent action constants must start with ACTION_: UPDATE_WIDGET")
    }

    @Test
    fun `reports constant properties accessed through Kotlin getters`() {
        lint()
            .allowMissingSdk()
            .files(
                intent,
                context,
                kotlin(
                    """
                    package test
                    import android.content.Intent

                    val UPDATE_WIDGET = "update"
                    object Actions {
                        val REFRESH_WIDGET = "refresh"
                    }
                    class Screen {
                        companion object {
                            const val OPEN_SCREEN = "open"
                        }
                        fun send(intent: Intent) {
                            Intent(UPDATE_WIDGET)
                            intent.action = Actions.REFRESH_WIDGET
                            intent.setAction((Screen.OPEN_SCREEN))
                        }
                    }
                    """,
                ).indented(),
            ).issues(IntentActionNamingDetector.ISSUE)
            .run()
            .expectErrorCount(3)
            .expectContains("Intent action constants must start with ACTION_: UPDATE_WIDGET")
            .expectContains("Intent action constants must start with ACTION_: REFRESH_WIDGET")
            .expectContains("Intent action constants must start with ACTION_: OPEN_SCREEN")
    }

    @Test
    fun `reports constants compared with intent actions and immutable local aliases`() {
        lint()
            .allowMissingSdk()
            .files(
                intent,
                context,
                kotlin(
                    """
                    package test
                    import android.content.Intent

                    const val COPY_DEBUG_INFO = "copy"
                    fun receive(intent: Intent) {
                        if ((intent.action) == COPY_DEBUG_INFO) return
                        if (COPY_DEBUG_INFO != intent.action) return
                        if (intent.getAction() == COPY_DEBUG_INFO) return
                        val action = intent.action
                        if (action == COPY_DEBUG_INFO) return
                        val alias = action
                        if (COPY_DEBUG_INFO != alias) return
                        when (intent.action) {
                            COPY_DEBUG_INFO -> return
                        }
                        when (alias) {
                            COPY_DEBUG_INFO -> return
                        }
                    }
                    """,
                ).indented(),
            ).issues(IntentActionNamingDetector.ISSUE)
            .run()
            .expectErrorCount(7)
            .expectContains("Intent action constants must start with ACTION_: COPY_DEBUG_INFO")
    }

    @Test
    fun `reports constants in explicit equals comparisons`() {
        lint()
            .allowMissingSdk()
            .files(
                intent,
                context,
                kotlin(
                    """
                    package test
                    import android.content.Intent

                    const val UPDATE_WIDGET = "update"
                    fun receive(intent: Intent) {
                        if (intent.action.equals(UPDATE_WIDGET)) return
                        if (UPDATE_WIDGET.equals(intent.action)) return
                        val action = intent.action
                        if (action.equals(UPDATE_WIDGET)) return
                        if (UPDATE_WIDGET.equals(action)) return
                    }
                    """,
                ).indented(),
            ).issues(IntentActionNamingDetector.ISSUE)
            .run()
            .expectErrorCount(4)
            .expectContains("Intent action constants must start with ACTION_: UPDATE_WIDGET")
    }

    @Test
    fun `allows compliant constants and actions that are not constants`() {
        lint()
            .allowMissingSdk()
            .files(
                intent,
                intentFilter,
                context,
                kotlin(
                    """
                    package test
                    import android.content.Context
                    import android.content.Intent
                    import android.content.IntentFilter

                    const val ACTION_UPDATE_WIDGET = "update"
                    object Actions {
                        val ACTION_REFRESH_WIDGET = "refresh"
                    }
                    class Screen {
                        companion object {
                            val dynamicAction = "action_" + System.nanoTime()
                            var mutableAction = "mutable"
                            val computedAction get() = "action_" + System.nanoTime()
                            val delegatedAction by lazy { "action_" + System.nanoTime() }
                        }
                        val instanceAction = "instance"
                        fun send(context: Context, intent: Intent, action: String) {
                            Intent(ACTION_UPDATE_WIDGET)
                            IntentFilter(ACTION_UPDATE_WIDGET)
                            IntentFilter.create(ACTION_UPDATE_WIDGET, "text/plain")
                            intent.action = Actions.ACTION_REFRESH_WIDGET
                            intent.setAction(ACTION_UPDATE_WIDGET)
                            IntentFilter().addAction(ACTION_UPDATE_WIDGET)
                            intent.action = dynamicAction
                            intent.action = mutableAction
                            intent.action = computedAction
                            intent.action = delegatedAction
                            intent.action = instanceAction
                            intent.action = action
                            intent.action = "literal"
                            Intent(action)
                            IntentFilter(action)
                            Intent(intent)
                            Intent(context, Screen::class.java)
                            if (intent.action == ACTION_UPDATE_WIDGET) return
                            if (intent.action == dynamicAction) return
                            if (intent.action == action) return
                        }
                    }
                    """,
                ).indented(),
            ).issues(IntentActionNamingDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `ignores unrelated methods properties and string arguments`() {
        lint()
            .allowMissingSdk()
            .files(
                intent,
                intentFilter,
                context,
                kotlin(
                    """
                    package test
                    import android.content.Intent
                    import android.content.IntentFilter

                    const val ACTION_UPDATE = "update"
                    const val OTHER = "other"
                    class Unrelated(val action: String) {
                        fun setAction(action: String) {}
                        fun addAction(action: String) {}
                        fun create(action: String, type: String) {}
                        fun send(intent: Intent) {
                            setAction(OTHER)
                            addAction(OTHER)
                            create(OTHER, OTHER)
                            IntentFilter(ACTION_UPDATE, OTHER)
                            IntentFilter.create(ACTION_UPDATE, OTHER)
                            intent.putExtra(OTHER, OTHER)
                            if (action == OTHER) return
                            var local = intent.action
                            local = OTHER
                            if (local == OTHER) return
                        }
                    }
                    """,
                ).indented(),
            ).issues(IntentActionNamingDetector.ISSUE)
            .run()
            .expectClean()
    }

    companion object {
        private val intent =
            java(
                """
                package android.content;
                public class Intent {
                    public Intent() {}
                    public Intent(String action) {}
                    public Intent(String action, Object uri) {}
                    public Intent(String action, Object uri, Context context, Class<?> cls) {}
                    public Intent(Context context, Class<?> cls) {}
                    public Intent(Intent other) {}
                    public Intent setAction(String action) { return this; }
                    public String getAction() { return null; }
                    public Intent putExtra(String name, String value) { return this; }
                }
                """,
            ).indented()

        private val intentFilter =
            java(
                """
                package android.content;
                public class IntentFilter {
                    public IntentFilter() {}
                    public IntentFilter(String action) {}
                    public IntentFilter(String action, String dataType) {}
                    public void addAction(String action) {}
                    public static IntentFilter create(String action, String dataType) { return null; }
                }
                """,
            ).indented()

        private val context =
            java(
                """
                package android.content;
                public class Context {}
                """,
            ).indented()
    }
}
