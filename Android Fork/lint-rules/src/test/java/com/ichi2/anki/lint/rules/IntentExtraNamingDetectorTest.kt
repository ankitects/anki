// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class IntentExtraNamingDetectorTest {
    @Test
    fun `reports constants in intent and helper calls`() {
        lint()
            .allowMissingSdk()
            .files(
                intent,
                intentCompat,
                intentExtensions,
                compatHelper,
                kotlin(
                    """
                    package test
                    import android.content.Intent
                    import androidx.core.content.IntentCompat
                    import com.ichi2.anki.common.utils.ext.getLongExtra
                    import com.ichi2.anki.common.utils.ext.getParcelableExtraCompat
                    import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableExtraCompat

                    const val NOTE_ID = "noteId"
                    class Screen {
                        companion object {
                            const val DECK_KEY = "deck"
                        }
                        fun read(intent: Intent) {
                            intent.putExtra(NOTE_ID, "1")
                            intent.putExtra(Screen.DECK_KEY, 1L)
                            intent.getStringExtra(NOTE_ID)
                            intent.getLongExtra(NOTE_ID, 0L)
                            intent.hasExtra(NOTE_ID)
                            intent.removeExtra(NOTE_ID)
                            IntentCompat.getParcelableExtra(intent, NOTE_ID, String::class.java)
                            intent.getLongExtra(NOTE_ID)
                            intent.getParcelableExtraCompat<String>(NOTE_ID)
                            intent.getSerializableExtraCompat<String>(NOTE_ID)
                        }
                    }
                    """,
                ).indented(),
            ).issues(IntentExtraNamingDetector.ISSUE)
            .run()
            .expectErrorCount(10)
            .expectContains("Intent extra key constants must start with EXTRA_: NOTE_ID")
            .expectContains("Intent extra key constants must start with EXTRA_: DECK_KEY")
    }

    @Test
    fun `reports extra key properties accessed through Kotlin getters`() {
        lint()
            .allowMissingSdk()
            .files(
                intent,
                kotlin(
                    """
                    package test
                    import android.content.Intent

                    val NOTE_ID = "noteId"
                    object Keys {
                        val DECK_KEY = "deck"
                    }
                    fun read(intent: Intent) {
                        intent.getStringExtra(NOTE_ID)
                        intent.putExtra(Keys.DECK_KEY, "1")
                    }
                    """,
                ).indented(),
            ).issues(IntentExtraNamingDetector.ISSUE)
            .run()
            .expectErrorCount(2)
            .expectContains("Intent extra key constants must start with EXTRA_: NOTE_ID")
            .expectContains("Intent extra key constants must start with EXTRA_: DECK_KEY")
    }

    @Test
    fun `allows compliant constants and keys that are not constants`() {
        lint()
            .allowMissingSdk()
            .files(
                intent,
                intentCompat,
                kotlin(
                    """
                    package test
                    import android.content.Intent
                    import androidx.core.content.IntentCompat

                    const val EXTRA_NOTE_ID = "noteId"
                    object Keys {
                        val EXTRA_DECK_ID = "deck"
                    }
                    class Screen {
                        companion object {
                            val dynamicKey = "extra_" + System.nanoTime()
                            var mutableKey = "mutable"
                            val computedKey get() = "extra_" + System.nanoTime()
                            val delegatedKey by lazy { "extra_" + System.nanoTime() }
                        }
                        val instanceKey = "instance"
                        fun read(intent: Intent, key: String) {
                            intent.getStringExtra(EXTRA_NOTE_ID)
                            intent.putExtra(Keys.EXTRA_DECK_ID, "1")
                            intent.getStringExtra(dynamicKey)
                            intent.getStringExtra(mutableKey)
                            intent.getStringExtra(computedKey)
                            intent.getStringExtra(delegatedKey)
                            intent.getStringExtra(instanceKey)
                            intent.getStringExtra(key)
                            intent.getStringExtra("literal")
                            IntentCompat.getParcelableExtra(intent, key, String::class.java)
                        }
                    }
                    """,
                ).indented(),
            ).issues(IntentExtraNamingDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `ignores values and unrelated methods with the same name`() {
        lint()
            .allowMissingSdk()
            .files(
                intent,
                kotlin(
                    """
                    package test
                    import android.content.Intent

                    const val EXTRA_NOTE = "note"
                    const val NOTE_ID = "noteId"
                    fun write(intent: Intent) {
                        intent.putExtra(EXTRA_NOTE, NOTE_ID)
                    }
                    class Unrelated {
                        fun putExtra(name: String, value: String) {}
                        fun send() = putExtra(NOTE_ID, "1")
                    }
                    """,
                ).indented(),
            ).issues(IntentExtraNamingDetector.ISSUE)
            .run()
            .expectClean()
    }

    companion object {
        private val intent =
            kotlin(
                """
                package android.content
                class Intent {
                    fun putExtra(name: String, value: String): Intent = this
                    fun putExtra(name: String, value: Long): Intent = this
                    fun getStringExtra(name: String): String? = null
                    fun getLongExtra(name: String, defaultValue: Long): Long = defaultValue
                    fun hasExtra(name: String): Boolean = false
                    fun removeExtra(name: String) {}
                }
                """,
            ).indented()
        private val intentCompat =
            kotlin(
                """
                package androidx.core.content
                import android.content.Intent
                object IntentCompat {
                    @JvmStatic
                    fun <T> getParcelableExtra(intent: Intent, name: String, clazz: Class<T>): T? = null
                }
                """,
            ).indented()
        private val intentExtensions =
            kotlin(
                """
                @file:JvmName("IntentKt")
                package com.ichi2.anki.common.utils.ext
                import android.content.Intent
                fun Intent.getLongExtra(key: String): Long? = null
                inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? = null
                """,
            ).indented()
        private val compatHelper =
            kotlin(
                """
                package com.ichi2.anki.compat
                import android.content.Intent
                class CompatHelper {
                    companion object {
                        inline fun <reified T> Intent.getSerializableExtraCompat(name: String): T? = null
                    }
                }
                """,
            ).indented()
    }
}
