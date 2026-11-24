// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class SavedStateKeyNamingDetectorTest {
    @Test
    fun `reports constants in saved state bundles`() {
        lint()
            .allowMissingSdk()
            .files(
                bundle,
                bundleCompat,
                bundleUtils,
                compatHelper,
                kotlin(
                    """
                    package test
                    import android.os.Bundle
                    import androidx.core.os.BundleCompat
                    import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
                    import com.ichi2.anki.utils.ext.getLongOrNull

                    const val NOTE_ID = "noteId"
                    object Keys {
                        val DECK_KEY = "deck"
                    }
                    class Screen {
                        companion object {
                            const val POSITION = "position"
                        }
                        fun onSaveInstanceState(outState: Bundle) {
                            outState.putLong(NOTE_ID, 1L)
                            outState.apply { putString(Keys.DECK_KEY, "deck") }
                        }
                        fun onCreate(savedInstanceState: Bundle?) {
                            savedInstanceState?.getLong(NOTE_ID)
                            savedInstanceState?.containsKey(Screen.POSITION)
                            savedInstanceState?.getLongOrNull(NOTE_ID)
                            savedInstanceState?.getSerializableCompat<String>(NOTE_ID)
                            if (savedInstanceState != null) BundleCompat.getParcelable(savedInstanceState, POSITION, String::class.java)
                        }
                        fun onViewStateRestored(savedInstanceState: Bundle?) {
                            savedInstanceState?.getString(Keys.DECK_KEY)
                        }
                    }
                    """,
                ).indented(),
            ).issues(SavedStateKeyNamingDetector.ISSUE)
            .run()
            .expectErrorCount(8)
            .expectContains("Saved instance state key constants must start with STATE_: NOTE_ID")
            .expectContains("Saved instance state key constants must start with STATE_: DECK_KEY")
            .expectContains("Saved instance state key constants must start with STATE_: POSITION")
    }

    @Test
    fun `allows compliant constants and keys that are not constants`() {
        lint()
            .allowMissingSdk()
            .files(
                bundle,
                kotlin(
                    """
                    package test
                    import android.os.Bundle

                    const val STATE_NOTE_ID = "noteId"
                    object Keys {
                        val STATE_DECK = "deck"
                    }
                    class Screen {
                        companion object {
                            val dynamicKey = "state_" + System.nanoTime()
                            var mutableKey = "mutable"
                        }
                        val instanceKey = "instance"
                        fun onSaveInstanceState(outState: Bundle) {
                            outState.putLong(STATE_NOTE_ID, 1L)
                            outState.putString(Keys.STATE_DECK, "deck")
                            outState.putString(dynamicKey, "x")
                            outState.putString(mutableKey, "x")
                            outState.putString(instanceKey, "x")
                            outState.putString("literal", "x")
                        }
                        fun onCreate(savedInstanceState: Bundle?) {
                            savedInstanceState?.getLong(STATE_NOTE_ID)
                        }
                    }
                    """,
                ).indented(),
            ).issues(SavedStateKeyNamingDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `ignores bundles that are not saved state`() {
        lint()
            .allowMissingSdk()
            .files(
                bundle,
                kotlin(
                    """
                    package test
                    import android.os.Bundle

                    const val NOTE_ID = "noteId"
                    const val STATE_NOTE = "note"
                    class Screen {
                        val arguments = Bundle()
                        fun onCreate(savedInstanceState: Bundle?) {
                            arguments.getLong(NOTE_ID)
                            Bundle().putLong(NOTE_ID, 1L)
                        }
                        fun newInstance(): Bundle = Bundle().apply { putLong(NOTE_ID, 1L) }
                        fun read(state: Bundle) = state.getLong(NOTE_ID)
                        fun onSaveInstanceState(outState: Bundle) {
                            outState.putString(STATE_NOTE, NOTE_ID)
                            val result = Bundle()
                            result.putLong(NOTE_ID, 1L)
                        }
                    }
                    """,
                ).indented(),
            ).issues(SavedStateKeyNamingDetector.ISSUE)
            .run()
            .expectClean()
    }

    companion object {
        private val bundle =
            kotlin(
                """
                package android.os
                open class BaseBundle {
                    fun containsKey(key: String): Boolean = false
                    fun getLong(key: String): Long = 0L
                    fun putLong(key: String, value: Long) {}
                    fun getString(key: String): String? = null
                    fun putString(key: String, value: String?) {}
                }
                class Bundle : BaseBundle()
                """,
            ).indented()
        private val bundleCompat =
            kotlin(
                """
                package androidx.core.os
                import android.os.Bundle
                object BundleCompat {
                    @JvmStatic
                    fun <T> getParcelable(bundle: Bundle, key: String, clazz: Class<T>): T? = null
                }
                """,
            ).indented()
        private val bundleUtils =
            kotlin(
                """
                @file:JvmName("BundleUtilsKt")
                package com.ichi2.anki.utils.ext
                import android.os.Bundle
                fun Bundle.getLongOrNull(key: String): Long? = null
                """,
            ).indented()
        private val compatHelper =
            kotlin(
                """
                package com.ichi2.anki.compat
                import android.os.Bundle
                class CompatHelper {
                    companion object {
                        inline fun <reified T> Bundle.getSerializableCompat(name: String): T? = null
                    }
                }
                """,
            ).indented()
    }
}
