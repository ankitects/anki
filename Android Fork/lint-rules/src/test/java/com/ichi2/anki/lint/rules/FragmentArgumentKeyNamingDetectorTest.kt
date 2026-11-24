// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.java
import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import org.junit.Test

class FragmentArgumentKeyNamingDetectorTest {
    @Test
    fun `reports constants read from fragment arguments`() {
        lint()
            .allowMissingSdk()
            .files(
                bundle,
                fragment,
                bundleCompat,
                bundleUtils,
                kotlin(
                    """
                    package test
                    import androidx.core.os.BundleCompat
                    import androidx.fragment.app.Fragment
                    import com.ichi2.anki.utils.ext.getLongOrNull

                    const val DECK_ID = "deckId"
                    object Keys {
                        val TITLE_KEY = "title"
                    }
                    class Screen : Fragment() {
                        companion object {
                            const val CARD_INDEX = "cardIndex"
                        }
                        private val deckId by lazy { requireArguments().getLong(DECK_ID) }
                        private val index = arguments?.getLong(CARD_INDEX)
                        fun read() {
                            requireArguments().getLong(DECK_ID)
                            arguments?.getString(Keys.TITLE_KEY)
                            val args = requireArguments()
                            args.containsKey(Screen.CARD_INDEX)
                            requireArguments().getLongOrNull(DECK_ID)
                            BundleCompat.getParcelable(requireArguments(), CARD_INDEX, String::class.java)
                            requireArguments().run { getString(Keys.TITLE_KEY) }
                        }
                    }
                    """,
                ).indented(),
            ).issues(FragmentArgumentKeyNamingDetector.ISSUE)
            .run()
            .expectErrorCount(8)
            .expectContains("Fragment argument key constants must start with ARG_: DECK_ID")
            .expectContains("Fragment argument key constants must start with ARG_: TITLE_KEY")
            .expectContains("Fragment argument key constants must start with ARG_: CARD_INDEX")
    }

    @Test
    fun `reports constants written to fragment arguments`() {
        lint()
            .allowMissingSdk()
            .files(
                bundle,
                fragment,
                kotlin(
                    """
                    package test
                    import android.os.Bundle
                    import androidx.fragment.app.Fragment

                    const val DECK_ID = "deckId"
                    const val NOTE_ID = "noteId"
                    const val CARD_ID = "cardId"
                    const val TITLE = "title"
                    fun getIntent(arguments: Bundle?) {}
                    class Screen : Fragment() {
                        companion object {
                            fun newInstance(deckId: Long) = Screen().apply { arguments = Bundle().apply { putLong(DECK_ID, deckId) } }
                            fun alias(fragment: Fragment) {
                                val args = Bundle()
                                args.putLong(NOTE_ID, 1L)
                                fragment.arguments = args
                            }
                            fun intent() = getIntent(arguments = Bundle().apply { putLong(CARD_ID, 1L) })
                        }
                        fun withDefault() {
                            arguments = arguments ?: Bundle().apply { putString(TITLE, "title") }
                        }
                    }
                    """,
                ).indented(),
            ).issues(FragmentArgumentKeyNamingDetector.ISSUE)
            .skipTestModes(TestMode.PARENTHESIZED)
            .run()
            .expectErrorCount(4)
            .expectContains("Fragment argument key constants must start with ARG_: DECK_ID")
            .expectContains("Fragment argument key constants must start with ARG_: NOTE_ID")
            .expectContains("Fragment argument key constants must start with ARG_: CARD_ID")
            .expectContains("Fragment argument key constants must start with ARG_: TITLE")
    }

    @Test
    fun `allows ARG_ and EXTRA_ constants and keys that are not constants`() {
        lint()
            .allowMissingSdk()
            .files(
                bundle,
                fragment,
                kotlin(
                    """
                    package test
                    import androidx.fragment.app.Fragment

                    const val ARG_DECK_ID = "deckId"
                    const val EXTRA_NOTE_ID = "noteId"
                    class Screen : Fragment() {
                        companion object {
                            val dynamicKey = "arg_" + System.nanoTime()
                            var mutableKey = "mutable"
                        }
                        fun read(key: String) {
                            requireArguments().getLong(ARG_DECK_ID)
                            arguments?.getLong(EXTRA_NOTE_ID)
                            requireArguments().getString(dynamicKey)
                            requireArguments().getString(mutableKey)
                            requireArguments().getString(key)
                            requireArguments().getString("literal")
                        }
                    }
                    """,
                ).indented(),
            ).issues(FragmentArgumentKeyNamingDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `ignores bundles that are not fragment arguments`() {
        lint()
            .allowMissingSdk()
            .files(
                bundle,
                fragment,
                kotlin(
                    """
                    package test
                    import android.os.Bundle
                    import androidx.fragment.app.Fragment

                    const val DECK_ID = "deckId"
                    const val ARG_NOTE = "note"
                    fun send(result: Bundle) {}
                    class Screen : Fragment() {
                        fun read(savedInstanceState: Bundle?, other: Bundle) {
                            savedInstanceState?.getLong(DECK_ID)
                            other.getLong(DECK_ID)
                            Bundle().putLong(DECK_ID, 1L)
                            send(Bundle().apply { putLong(DECK_ID, 1L) })
                            requireArguments().getString(ARG_NOTE, DECK_ID)
                        }
                    }
                    class NotAFragment {
                        var arguments = Bundle()
                        fun requireArguments() = arguments
                        fun read() {
                            requireArguments().getLong(DECK_ID)
                            arguments.getLong(DECK_ID)
                            arguments = Bundle().apply { putLong(DECK_ID, 1L) }
                        }
                    }
                    """,
                ).indented(),
            ).issues(FragmentArgumentKeyNamingDetector.ISSUE)
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
                    fun getString(key: String, defaultValue: String?): String? = defaultValue
                }
                class Bundle : BaseBundle()
                """,
            ).indented()
        private val fragment =
            java(
                """
                package androidx.fragment.app;
                import android.os.Bundle;
                public class Fragment {
                    public Bundle getArguments() { return null; }
                    public void setArguments(Bundle args) {}
                    public final Bundle requireArguments() { return new Bundle(); }
                }
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
    }
}
